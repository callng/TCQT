package com.owo233.tcqt.hooks

import android.app.Activity
import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList

@RegisterAction
@RegisterSetting(key = "fake_multi_window_status", name = "伪装多窗口状态", type = SettingType.BOOLEAN, defaultValue = "false")
class FakeMultiWindowStatus: IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        Activity::class.java.getDeclaredMethod("isInMultiWindowMode")
            .hookMethod(afterHook {
                it.result = false
            })

        Activity::class.java.getDeclaredMethod("isInPictureInPictureMode")
            .hookMethod(afterHook {
                it.result = false
            })
    }

    override val key: String get() = GeneratedSettingList.FAKE_MULTI_WINDOW_STATUS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
