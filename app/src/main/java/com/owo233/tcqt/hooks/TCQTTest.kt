package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction

@RegisterAction(false)
@RegisterSetting(
    key = "tcqt_test1",
    name = "tcqt_test1",
    type = SettingType.BOOLEAN,
    desc = "tcqt_test1",
    uiTab = "测试1"
)
@RegisterSetting(
    key = "tcqt_test2",
    name = "tcqt_test2",
    type = SettingType.BOOLEAN,
    desc = "tcqt_test2",
    uiTab = "测试2"
)
@RegisterSetting(
    key = "tcqt_test3",
    name = "tcqt_test3",
    type = SettingType.BOOLEAN,
    desc = "tcqt_test3",
    uiTab = "测试3"
)
@RegisterSetting(
    key = "tcqt_test4",
    name = "tcqt_test4",
    type = SettingType.BOOLEAN,
    desc = "tcqt_test4",
    uiTab = "测试4"
)
class TCQTTest : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {}

    override val key: String = "TCQTTest" //测试用的

    override fun canRun(): Boolean = false
}
