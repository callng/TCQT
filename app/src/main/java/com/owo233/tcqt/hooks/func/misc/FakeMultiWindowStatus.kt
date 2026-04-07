package com.owo233.tcqt.hooks.func.misc

import android.app.Activity
import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hook.hookBefore

@RegisterAction
@RegisterSetting(
    key = "fake_multi_window_status",
    name = "伪装多窗口状态",
    type = SettingType.BOOLEAN,
    desc = "不知道有什么用。",
    uiTab = "杂项"
)
class FakeMultiWindowStatus : IAction {

    override fun onRun(app: Application, process: ActionProcess) {
        Activity::class.java.getDeclaredMethod("isInMultiWindowMode")
            .hookBefore { it.result = false }

        Activity::class.java.getDeclaredMethod("isInPictureInPictureMode")
            .hookBefore { it.result = false }
    }

    override val key: String get() = GeneratedSettingList.FAKE_MULTI_WINDOW_STATUS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
