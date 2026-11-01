package com.owo233.tcqt.core.action

import android.app.Application
import android.content.Context
import com.owo233.tcqt.core.config.Setting
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.core.log.Log

enum class ActionUiType {
    SWITCH, ENTRY
}

typealias ActionProcess = com.owo233.tcqt.annotations.ActionProcess

/**
 * 功能启动优先级，由 [StartupScheduler] 据此分批安装。
 *
 * 宿主 `BaseApplicationImpl.onCreate` 的 Before 回调只**同步**安装 [CRITICAL]，
 * 其余优先级都在 onCreate 返回后转后台线程安装。
 */
typealias ActionPriority = com.owo233.tcqt.annotations.ActionPriority

/**
 * 注册表与设置界面的内部功能模型 —— **功能作者不要实现它**。
 *
 * 作者唯一该继承的是 `com.owo233.tcqt.api.Feature`（或 `api.InfraTask`），由它实现本接口。
 * [ActionRegistry] 与设置界面都靠本接口读取功能元数据，而 `core` / `ui` 都不允许依赖 `api`，
 * 因此本接口必须留在 `core`。
 */
interface ActionSpec {

    val key: String
    val name: String
    val desc: String get() = ""

    /**
     * 设置界面分类，**由类所在的 `features/<分类>/` 包路径唯一决定**，功能不要声明它。
     *
     * 包名到标签的翻译由 [FeatureCategories] 负责。
     */
    val uiTab: String
        get() = FeatureCategories.labelOf(this.javaClass.name) ?: FeatureCategories.FALLBACK
    val uiOrder: Int get() = 1000
    val hidden: Boolean get() = false
    val defaultEnabled: Boolean get() = false
    val uiType: ActionUiType get() = ActionUiType.SWITCH

    val settings: List<Setting<*>> get() = emptyList()

    val processes: Set<ActionProcess> get() = ActionRegistry.metadataFor(javaClass)?.processes ?: DEFAULT_PROCESSES

    /**
     * 启动优先级，默认 [ActionPriority.DEFERRED]。
     *
     * 仅当目标方法在宿主 [android.app.Application.onCreate] 执行期间就会被调用、
     * 且第一次调用不能漏时才提升为 [ActionPriority.CRITICAL]。
     */
    val priority: ActionPriority get() = ActionRegistry.metadataFor(javaClass)?.priority ?: ActionPriority.DEFERRED

    /**
     * 配置项的动态描述；返回 null 时回退到静态描述。
     */
    fun getSettingDesc(key: String): String? = null

    operator fun invoke(app: Application, process: ActionProcess) {
        ActionErrorStore.withAction(key) {
            // 每次宿主启动都重新开始健康检查，下方任一失败都会立即上报。
            ActionErrorStore.clear(key, com.owo233.tcqt.core.env.HookEnv.processName)
            runCatching {
                if (canRun() && onInit()) {
                    onRun(app, process)
                }
            }.onFailure {
                ActionErrorStore.report(key, "功能初始化", it)
                Log.e("功能 [${ActionRegistry.resolve(this)}] 执行异常", it)
            }
        }
    }

    fun onRun(app: Application, process: ActionProcess)

    fun onUiClick(context: Context): Boolean = false

    fun canRun(): Boolean {
        return uiType != ActionUiType.ENTRY && runCatching {
            TCQTSetting.getValue<Boolean>(key) ?: defaultEnabled
        }.getOrElse { e ->
            ActionErrorStore.report(key, "开关检查", e)
            Log.e("功能 [${ActionRegistry.resolve(this)}] 开关检查异常", e)
            defaultEnabled
        }
    }

    /**
     * 功能执行条件判断；设置界面会调用它来决定是否禁用该功能。
     *
     * 必须保持纯条件判断，不要在这里做 Hook 安装、配置写入等副作用（放 [onRun]）。
     * 返回 true 才会继续执行 [onRun]。
     */
    fun onInit(): Boolean = true

    companion object {
        val DEFAULT_PROCESSES = setOf(ActionProcess.MAIN)
    }
}
