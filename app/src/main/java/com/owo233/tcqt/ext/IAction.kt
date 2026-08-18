package com.owo233.tcqt.ext

import android.app.Application
import android.content.Context
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.log.ActionErrorStore

enum class ActionUiType {
    SWITCH, ENTRY
}

enum class ActionProcess {

    MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV,
    OTHER, ALL
}

/**
 * 功能启动优先级。
 *
 * 宿主 `BaseApplicationImpl.onCreate` 的 Before 回调里只会**同步**安装
 * [CRITICAL]，其余优先级由 [com.owo233.tcqt.StartupScheduler] 在
 * onCreate 返回后于后台分批安装，从而让「白屏时间」不再随启用功能数量线性增长。
 */
enum class ActionPriority {

    /**
     * 必须在宿主 Application.onCreate 返回之前同步安装。
     *
     * 只允许「目标方法在 onCreate 执行期间就会被调用，且第一次调用不能漏」的
     * 功能使用（例如 [com.owo233.tcqt.hooks.func.advanced.FileRecvRedirect]）。
     * 数量必须严格控制，否则白屏时间会随 CRITICAL 数量线性增长。
     */
    CRITICAL,

    /**
     * onCreate 返回后立刻安装（主线程 Handler post 后转入后台线程）。
     * 目标方法在 Activity / 登录流程早期被调用，但不会在 onCreate 内被调用。
     */
    EARLY,

    /**
     * 默认值。MAIN 进程等首帧后、后台进程立刻，分批在后台线程安装。
     * 目标方法在用户与界面交互之后才会被调用（聊天、设置、WebView 等）。
     */
    DEFERRED,

    /** 最后一批安装，允许与其他初始化错峰。 */
    BACKGROUND,
}

interface IAction {

    val key: String
    val name: String
    val desc: String get() = ""
    val uiTab: String get() = "基础"
    val uiOrder: Int get() = 1000
    val hidden: Boolean get() = false
    val defaultEnabled: Boolean get() = false
    val uiType: ActionUiType get() = ActionUiType.SWITCH

    val settings: List<Setting<*>> get() = emptyList()

    val processes: Set<ActionProcess> get() = DEFAULT_PROCESSES

    /**
     * 启动优先级，默认 [ActionPriority.DEFERRED]。
     *
     * 绝大多数功能不需要覆盖：只有目标方法在宿主
     * [android.app.Application.onCreate] 执行期间就会被调用、且第一次调用
     * 不能漏时，才应提升为 [ActionPriority.CRITICAL]。
     */
    val priority: ActionPriority get() = ActionPriority.DEFERRED

    /**
     * 获取配置项的动态描述
     * @param key 配置项键名
     * @return 动态描述内容，返回 null 时将使用静态描述作为后备
     */
    fun getSettingDesc(key: String): String? = null

    operator fun invoke(app: Application, process: ActionProcess) {
        ActionErrorStore.withAction(key) {
            // A host restart starts a fresh health check for this feature in
            // this process. Any failure below (or in a later hook callback)
            // writes the error back immediately.
            ActionErrorStore.clear(key, com.owo233.tcqt.HookEnv.processName)
            runCatching {
                if (canRun() && onInit()) {
                    onRun(app, process)
                }
            }.onFailure {
                ActionErrorStore.report(key, "功能初始化", it)
                Log.e("功能 [${ActionManager.resolve(this)}] 执行异常", it)
            }
        }
    }

    fun onRun(app: Application, process: ActionProcess)

    fun onUiClick(context: Context): Boolean = false

    fun canRun(): Boolean {
        if (uiType == ActionUiType.ENTRY) return false
        return runCatching {
            TCQTSetting.getValue<Boolean>(key) ?: defaultEnabled
        }.getOrElse { e ->
            ActionErrorStore.report(key, "开关检查", e)
            Log.e("功能 [${ActionManager.resolve(this)}] 开关检查异常", e)
            defaultEnabled
        }
    }

    /**
     * 初始化逻辑
     * @return true 表示继续执行后续 onRun 函数，false 则不执行
     */
    fun onInit(): Boolean = true

    companion object {
        val DEFAULT_PROCESSES = setOf(ActionProcess.MAIN)
    }
}

/**
 * 无视设置开关条件的 Action
 */
abstract class AlwaysRunAction : IAction {

    override val key: String = "not_empty"
    override val name: String = ""
    override val hidden: Boolean = true
    override val processes: Set<ActionProcess> = IAction.DEFAULT_PROCESSES
    override fun canRun(): Boolean = true
}
