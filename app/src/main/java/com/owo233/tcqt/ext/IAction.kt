package com.owo233.tcqt.ext

import android.content.Context
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.Log

enum class ActionProcess {
    MSF, MAIN, TOOL, OPENSDK, QZONE,
    OTHER, // 非上述五个
    ALL, // 全部进程
}

/**
 * 无视设置开关条件的Action
 */
abstract class AlwaysRunAction : IAction {
    override val key: String
        get() = ""

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN)

    override fun canRun(): Boolean = true
}

interface IAction {

    operator fun invoke(ctx: Context, process: ActionProcess) {
        runCatching {
            if (canRun()) onRun(ctx, process)
        }.onFailure {
            Log.e("Action [${ActionManager.resolve(this)}] invoke 异常", it)
        }
    }

    fun onRun(ctx: Context, process: ActionProcess)

    fun canRun(): Boolean = runCatching {
        GeneratedSettingList.getBoolean(key)
    }.getOrElse { e ->
        Log.e("Action [${ActionManager.resolve(this)}] canRun 检查异常", e)
        false
    }

    val key: String

    val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
