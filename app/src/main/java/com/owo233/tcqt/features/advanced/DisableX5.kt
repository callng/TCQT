package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodBefore

@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.TOOL])
object DisableX5 : Feature(
    key = "disable_x5",
    name = "禁用X5内核",
    desc = "强制QQ内置浏览器使用系统webview。",
) {


    override fun install() {
        loadOrThrow("com.tencent.smtt.sdk.QbSdk").hookMethodBefore(
            "getIsSysWebViewForcedByOuter"
        ) { param ->
            param.result = true
        }
    }

}
