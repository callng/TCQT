package com.owo233.tcqt.ext

import android.app.Application
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.log.Log

enum class ActionProcess {
    MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV,
    OTHER, ALL
}

interface IAction {

    val key: String

    val processes: Set<ActionProcess> get() = DEFAULT_PROCESSES

    operator fun invoke(app: Application, process: ActionProcess) {
        runCatching {
            if (canRun() && onInit()) onRun(app, process) else return@runCatching
        }.onFailure {
            Log.e("功能 [${ActionManager.resolve(this)}] 执行异常", it)
        }
    }

    fun onRun(app: Application, process: ActionProcess)

    fun canRun(): Boolean = runCatching {
        GeneratedSettingList.getBoolean(key)
    }.getOrElse { e ->
        Log.e("功能 [${ActionManager.resolve(this)}] 开关检查异常", e)
        false
    }

    /**
     * 初始化逻辑
     * @return true 表示继续执行后续函数，false 则拦截
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
    override val key: String = ""
    override val processes: Set<ActionProcess> = IAction.DEFAULT_PROCESSES
    override fun canRun(): Boolean = true
}
