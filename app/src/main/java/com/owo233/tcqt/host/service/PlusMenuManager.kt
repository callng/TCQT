package com.owo233.tcqt.host.service

import android.app.Activity
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.ResourcesUtils
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.reflect.getObject
import java.util.concurrent.CopyOnWriteArrayList
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

/**
 * 消息列表右上角加号菜单的注册表。
 *
 * 本类不是 Action：条目由各功能在自己的 `install()` 里注册（**只在该功能启用时执行**），
 * 菜单 hook 由 [ensureHooksInstalled] 幂等安装 —— 这样多个功能共用同一套 hook，
 * 不会互相把条目重复注入，也不会因为某一个功能（如 `AddPlusMenu`）关闭而一起失效。
 */
object PlusMenuManager {

    private val items = CopyOnWriteArrayList<ExtraMenuItem>()

    /** hook 只装一次，避免多个功能各自注入导致菜单项重复。 */
    @Volatile
    private var hooksInstalled = false

    fun register(item: ExtraMenuItem) {
        items.add(item)
    }

    fun registerAll(vararg menuItems: ExtraMenuItem) {
        items.addAll(menuItems)
    }

    /** 按 id 查找已注册条目，供点击回调派发。 */
    fun findById(id: Int): ExtraMenuItem? = items.find { it.id == id }

    /** 按 id 排序后构造宿主 `PopupMenuDialog$MenuItem` 实例。 */
    fun buildMenuItems(): List<Any> {
        val clazz = loadOrThrow($$"com.tencent.widget.PopupMenuDialog$MenuItem")
        return items.sortedBy { it.id }.map { item ->
            clazz.getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(item.id, item.title, item.title, item.iconResId)
        }
    }

    /**
     * 幂等安装「加号菜单」的构建与点击 hook。
     *
     * 宿主 `conversationPlusBuild` 在首页初始化时就会构建菜单，所以调用方应当用
     * `ActionPriority.EARLY` 保证在本方法在构建前执行。
     *
     * @param dexKitTask 提供 `AddPlusMenu` 那个 `onClickAction` 的 DexKit 查询；每个
     *   调用方传入自己的任务即可（查询体相同，DexKit 内部会缓存）。
     */
    fun ensureHooksInstalled(dexKitTask: DexKitTask) {
        if (hooksInstalled) return
        synchronized(this) {
            if (hooksInstalled) return
            hookBuild()
            hookClick(dexKitTask)
            hooksInstalled = true
        }
    }

    private fun hookBuild() {
        loadOrThrow("com.tencent.widget.PopupMenuDialog")
            .hookMethodBefore(
                "conversationPlusBuild",
                Activity::class.java,
                List::class.java,
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener"),
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnDismissListener")
            ) { param ->
                val activity = param.args[0] as Activity
                ResourcesUtils.injectResourcesToContext(activity.resources)
                param.args[1] = (param.args[1] as List<*>) + buildMenuItems()
            }
    }

    private fun hookClick(dexKitTask: DexKitTask) {
        dexKitTask.requireMethod(PLUS_MENU_CLICK_QUERY).hookBefore { param ->
            val clickedId = param.args[0]!!.getObject("id") as Int
            findById(clickedId)?.let {
                it.onClick()
                param.result = Unit
            }
        }
    }

    /** `onClickAction` 的 DexKit 查询名，供各功能在 `getQueryMap` 里复用同一份定义。 */
    const val PLUS_MENU_CLICK_QUERY = "PlusMenuClickAction"

    /** 构建该查询的匹配器。 */
    fun clickActionMatcher(): BaseMatcher = FindMethod().apply {
        searchPackages("com.tencent.mobileqq.activity.recent")
        matcher {
            name = "onClickAction"
            paramTypes($$"com.tencent.widget.PopupMenuDialog$MenuItem")
            declaredClass {
                addInterface($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener")
            }
        }
    }
}

data class ExtraMenuItem(
    val id: Int,
    val title: String,
    val iconResId: Int,
    val onClick: () -> Unit
)
