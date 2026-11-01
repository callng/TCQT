package com.owo233.tcqt.features.misc

import android.app.Activity
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookBefore

@RegisterAction(processes = [ActionProcess.ALL])
object FakeMultiWindowStatus : Feature(
    key = "fake_multi_window_status",
    name = "伪装多窗口状态",
    desc = "不知道有什么用。",
) {


    override fun install() {
        Activity::class.java.getDeclaredMethod("isInMultiWindowMode")
            .hookBefore { it.result = false }

        Activity::class.java.getDeclaredMethod("isInPictureInPictureMode")
            .hookBefore { it.result = false }
    }

}
