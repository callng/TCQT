package com.owo233.tcqt.hooks.func

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.internals.setting.TCQTBrowserInterface
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.smtt.sdk.WebView

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        WebView::class.java.getMethod("loadUrl", String::class.java)
            .hookBeforeMethod { param ->
                val web = param.thisObject as WebView
                web.addJavascriptInterface(TCQTBrowserInterface(ctx), "TCQTBrowser")
            }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
