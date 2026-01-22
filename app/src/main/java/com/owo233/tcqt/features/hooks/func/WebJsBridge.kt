package com.owo233.tcqt.features.hooks.func

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.AlwaysRunAction
import com.owo233.tcqt.foundation.internal.setting.TCQTBrowserInterface
import com.owo233.tcqt.foundation.internal.setting.TCQTJsInterface
import com.owo233.tcqt.foundation.internal.setting.TCQTSetting
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.tencent.smtt.sdk.WebView
import java.net.URL

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        WebView::class.java.getMethod("loadUrl", String::class.java)
            .hookBeforeMethod { param ->
                val web = param.thisObject as WebView
                val url = param.args[0] as String
                if (URL(url).host == URL(TCQTSetting.settingUrl).host) {
                    web.addJavascriptInterface(TCQTJsInterface(ctx), "TCQT")
                    web.loadDataWithBaseURL(
                        null,
                        TCQTSetting.getSettingHtml(),
                        "text/html",
                        "utf-8",
                        null
                    )
                    param.result = Unit
                } else {
                    // 其他url
                    web.addJavascriptInterface(TCQTBrowserInterface(ctx), "TCQTBrowser")
                }
            }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
