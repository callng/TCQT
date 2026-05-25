package com.owo233.tcqt.hooks.func.misc

import android.app.Activity
import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore

@RegisterAction
class FakeMultiWindowStatus : IAction {

    override val name: String get() = "伪装多窗口状态"
    override val desc: String get() = "不知道有什么用。"
    override val uiTab: String get() = "杂项"

    override fun onRun(app: Application, process: ActionProcess) {
        Activity::class.java.getDeclaredMethod("isInMultiWindowMode")
            .hookBefore { it.result = false }

        Activity::class.java.getDeclaredMethod("isInPictureInPictureMode")
            .hookBefore { it.result = false }
    }

    override val key: String get() = "fake_multi_window_status"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
