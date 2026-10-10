package com.owo233.tcqt.features.internal

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.config.TCQTBrowserInterface
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.smtt.sdk.WebView
import java.net.URL

@RegisterAction
object WebJsBridge : InfraTask(
    key = "WebJsBridge",
    processes = setOf(ActionProcess.TOOL),
) {

    override fun install() {
        WebView::class.java.getMethod("loadUrl", String::class.java)
            .hookBefore { param ->
                val url = param.args[0] as String

                when {
                    isSettingPageUrl(url) -> handleSettingPageRedirect(param)
                    !PlatformTools.isHostWhitelisted(url) -> injectJavascriptInterface(
                        param,
                        hostApp
                    )
                }
            }
    }

    private fun isSettingPageUrl(url: String): Boolean =
        runCatching { URL(url).host == URL(SETTING_URL).host }.getOrDefault(false)

    private fun handleSettingPageRedirect(param: MethodHookParam) {
        param.result = Unit

        val context = QQInterfaces.topActivity ?: return
        runCatching {
            ModuleScope.launchMain {
                val latestLoader = System.getProperties()["tcqt.module_class_loader"] as? ClassLoader
                    ?: this.javaClass.classLoader
                val settingActivityClass =
                    latestLoader.loadClass(ModuleComponents.SETTINGS_ACTIVITY)
                val intent = Intent(context, settingActivityClass)
                context.startActivity(intent)
                context.finish()
                context.clearTransition()
            }
        }.onFailure {
            Toasts.error("需要重新启动${HookEnv.appName}")
        }
    }

    private fun injectJavascriptInterface(param: MethodHookParam, app: Application) {
        val webView = param.thisObject as WebView
        webView.addJavascriptInterface(TCQTBrowserInterface(app), "TCQTBrowser")
    }

    private fun Activity.clearTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                0, 0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private const val SETTING_URL = "http://tcqt.qq.com/"
}
