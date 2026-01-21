package com.owo233.tcqt.hooks.func.advanced

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookBeforeMethod

@RegisterAction
@RegisterSetting(
    key = "disable_x5",
    name = "禁用X5内核",
    type = SettingType.BOOLEAN,
    desc = "强制QQ内置浏览器使用系统webview。",
    uiTab = "高级"
)
class DisableX5 : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.smtt.sdk.QbSdk").hookBeforeMethod(
            "getIsSysWebViewForcedByOuter"
        ) { param ->
            param.result = true
        }
    }

    override val key: String
        get() = GeneratedSettingList.DISABLE_X5

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN, ActionProcess.TOOL)
}
