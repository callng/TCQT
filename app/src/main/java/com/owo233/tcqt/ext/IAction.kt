package com.owo233.tcqt.ext

import android.app.Application
import android.content.Context
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.log.Log

enum class ActionUiType {
    SWITCH, ENTRY
}

enum class ActionProcess {

    MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV,
    OTHER, ALL
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
     * 获取配置项的动态描述
     * @param key 配置项键名
     * @return 动态描述内容，返回 null 时将使用静态描述作为后备
     */
    fun getSettingDesc(key: String): String? = null

    operator fun invoke(app: Application, process: ActionProcess) {
        runCatching {
            if (canRun() && onInit()) {
                onRun(app, process)
            }
        }.onFailure {
            Log.e("功能 [${ActionManager.resolve(this)}] 执行异常", it)
        }
    }

    fun onRun(app: Application, process: ActionProcess)

    fun onUiClick(context: Context): Boolean = false

    fun canRun(): Boolean {
        if (uiType == ActionUiType.ENTRY) return false
        return runCatching {
            TCQTSetting.getValue<Boolean>(key) ?: defaultEnabled
        }.getOrElse { e ->
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
