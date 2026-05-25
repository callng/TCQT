package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookMethodBefore

@RegisterAction
class DisableX5 : IAction {

    override val name: String get() = "禁用X5内核"
    override val desc: String get() = "强制QQ内置浏览器使用系统webview。"
    override val uiTab: String get() = "高级"

    override fun onRun(app: Application, process: ActionProcess) {
        loadOrThrow("com.tencent.smtt.sdk.QbSdk").hookMethodBefore(
            "getIsSysWebViewForcedByOuter"
        ) { param ->
            param.result = true
        }
    }

    override val key: String
        get() = "disable_x5"

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN, ActionProcess.TOOL)
}
