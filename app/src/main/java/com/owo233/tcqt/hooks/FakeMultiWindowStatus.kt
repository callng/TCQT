package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod

class FakeMultiWindowStatus: IAction {

    override fun onRun(ctx: Context) {
        Activity::class.java.getDeclaredMethod("isInMultiWindowMode")
            .hookMethod(afterHook {
                it.result = false
            })
    }

    override val name: String get() = "伪装多窗口状态"

    override val process: ActionProcess get() = ActionProcess.MAIN
}
