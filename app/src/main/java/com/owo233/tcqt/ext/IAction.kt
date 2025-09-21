package com.owo233.tcqt.ext

import android.content.Context
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.Log

enum class ActionProcess {
    MSF, MAIN, TOOL, OPENSDK,
    OTHER, // 非上述四个
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
            Log.e("invoke Action 异常", it)
        }
    }

    fun onRun(ctx: Context, process: ActionProcess)

    fun canRun(): Boolean = GeneratedSettingList.getBoolean(key)

    val key: String

    val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
