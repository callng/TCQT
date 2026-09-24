package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.advanced.ChatFoldEnabler.foldDays
import com.owo233.tcqt.features.advanced.ChatFoldEnabler.minKeepCount
import com.owo233.tcqt.host.service.ExtraMenuItem
import com.owo233.tcqt.host.service.PlusMenuManager
import org.luckypray.dexkit.query.base.BaseMatcher

/**
 * 首页折叠 N 天前的会话。
 *
 * 实现类是 `com.tencent.qqnt.chats.main.vm.usecase.g`（日志 TAG `OldChatsFoldUseCase`）：
 * - `a(List, int)` 组装列表，先看实例字段 `a`（是否折叠），为 false 直接原样返回；
 * - `c(boolean, int, int)` 由 `ChatsListVM.R/H` 注入折叠开关、天数、最少保留条数。
 *
 * 服务端不给灰度时 `c(...)` 不会被调用（`a` 恒为 false），所以在列表组装前强制打开；
 * 由于 QQ 只在"未展开"时才把折叠条插进列表，展开后折叠条会消失且没有任何入口能收回，
 * 因此额外在加号菜单里提供一个主动折叠入口（方案 A：不跟状态机对抗）。
 */
@RegisterAction
object ChatFoldEnabler : Feature(
    key = "chat_fold_enabler",
    name = "首页折叠旧会话",
    desc = "强制开启首页消息列表「折叠 N 天前的会话」灰度，并可自定义折叠天数与最少保留条数。",
    priority = ActionPriority.EARLY,
    requires = Requires(host = Requires.Host.QQOnly, minQQVersion = QQVersion.QQ_9_3_70),
), DexKitTask {

    private val foldDays by intOption(
        settingKey = "fold_days",
        name = "折叠天数",
        defaultValue = 1,
        desc = "超过该天数未活跃的会话会被折叠。",
        options = listOf("7天前", "14天前", "28天前", "60天前", "90天前"),
    )

    private val minKeepCount by intOption(
        settingKey = "min_keep_count",
        name = "最少保留条数",
        defaultValue = 1,
        desc = "列表最前面至少保留多少条会话不参与折叠。",
        options = listOf("1条", "3条", "5条", "10条"),
    )

    override fun install() {
        val useCaseClz = runCatching {
            loadOrThrow("com.tencent.qqnt.chats.main.vm.usecase.g")
        }.getOrNull()
        if (useCaseClz == null) {
            Log.e("$TAG 找不到 OldChatsFoldUseCase，本版 QQ 可能已改混淆名")
            return
        }

        val foldItemClz = runCatching {
            loadOrThrow("com.tencent.qqnt.chats.core.adapter.itemdata.g")
        }.getOrNull()

        // ── 列表组装：首次强制打开折叠 ──────────────────────────────────────
        // 只在折叠真正建立之前干预；一旦折叠条目出现就收手，把展开/收起交还给 QQ，
        useCaseClz.hookMethodBefore(
            "a",
            List::class.java,
            Int::class.javaPrimitiveType
        ) { param ->
            useCaseInstance = param.thisObject
            if (foldingEnabled) return@hookMethodBefore
            if (!reportedForcing) {
                reportedForcing = true
                // Log.i("$TAG 强制开启折叠，天数=${days()}，保留=${keep()}")
            }
            forceFoldFields(param.thisObject)
        }

        if (foldItemClz != null) {
            useCaseClz.hookMethodAfter(
                "a",
                List::class.java,
                Int::class.javaPrimitiveType
            ) { param ->
                if (foldingEnabled) return@hookMethodAfter
                val produced = (param.result as? List<*>)?.any { foldItemClz.isInstance(it) } == true
                if (produced) {
                    foldingEnabled = true
                    // Log.i("$TAG 折叠已建立，后续展开/收起交还给 QQ")
                }
            }
        }

        // ── 主动入口：加号菜单 ──────────────────────────────────────────────
        PlusMenuManager.register(
            ExtraMenuItem(
                id = MENU_ID_FOLD_NOW,
                title = "折叠旧会话",
                iconResId = android.R.drawable.ic_menu_sort_by_size,
                onClick = { foldNow() }
            )
        )
        runCatching { PlusMenuManager.ensureHooksInstalled(this) }
            .onFailure { Log.e("$TAG 加号菜单 hook 安装失败：${it.message}") }
    }

    /** 主动折叠：按设置重新驱动 UseCase，等价于用户点了一次"折叠"。 */
    private fun foldNow() {
        val useCase = useCaseInstance
        if (useCase == null) {
            Log.e("$TAG 主动折叠失败：UseCase 尚未就绪（首页列表还没组装过）")
            return
        }
        runCatching {
            useCaseType?.getMethod(
                "c",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )?.invoke(useCase, true, days(), keep())
            foldingEnabled = false
            // Log.i("$TAG 已主动折叠，天数=${days()}，保留=${keep()}")
        }.onFailure { Log.e("$TAG 主动折叠失败：${it.message}") }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        PlusMenuManager.PLUS_MENU_CLICK_QUERY to PlusMenuManager.clickActionMatcher()
    )

    /** 把 UseCase 实例的三个状态字段按设置写回"已开启"。 */
    private fun forceFoldFields(target: Any) {
        val clz = target.javaClass
        // 混淆字段名，已按 9.3.70.41925 实测：a:Z / b:I / c:I
        clz.findFieldOrNull("a")?.set(target, true)
        clz.findFieldOrNull("b")?.set(target, days())
        clz.findFieldOrNull("c")?.set(target, keep())
        useCaseType = clz
    }

    /** [foldDays] 是 `intOption` 的 1-based 下标，映射回天数。 */
    private fun days(): Int = when (foldDays) {
        1 -> 7
        2 -> 14
        3 -> 28
        4 -> 60
        5 -> 90
        else -> 28
    }

    /** [minKeepCount] 是 `intOption` 的 1-based 下标，映射回条数。 */
    private fun keep(): Int = when (minKeepCount) {
        1 -> 1
        2 -> 3
        3 -> 5
        4 -> 10
        else -> 1
    }

    private fun Class<*>.findFieldOrNull(name: String) =
        runCatching {
            getDeclaredField(name).apply { isAccessible = true }
        }.getOrNull()

    private const val TAG = "ChatFoldEnabler"

    /** 加号菜单条目 id */
    private const val MENU_ID_FOLD_NOW = 23341

    /** 折叠是否已建立。建立之后不再干预，把展开/收起完全交还给 QQ。 */
    @Volatile
    private var foldingEnabled = false

    /** 日志只打一次，避免每次列表组装刷屏。 */
    @Volatile
    private var reportedForcing = false

    /** UseCase 类，供主动折叠时反射调用 `c(ZII)`。 */
    @Volatile
    private var useCaseType: Class<*>? = null

    /** 组装列表时捕获的 UseCase 实例，主动折叠要用它。 */
    @Volatile
    private var useCaseInstance: Any? = null
}
