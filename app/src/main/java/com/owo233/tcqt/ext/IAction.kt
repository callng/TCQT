package com.owo233.tcqt.ext

import android.content.Context
import com.owo233.tcqt.utils.logE

enum class ActionProcess {
    MSF, MAIN, TOOL, INVALID
}

interface IAction {
    operator fun invoke(ctx: Context) {
        runCatching {
            if (canRun(ctx)) onRun(ctx)
        }.onFailure {
            logE(msg = "invoke Action 异常", cause = it)
        }
    }

    fun onRun(ctx: Context)

    fun canRun(ctx: Context): Boolean = true // TODO 需要更详细的机制

    val name: String

    val process: ActionProcess
}
