package com.owo233.tcqt.hooks.func

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.activity.SettingActivity
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTBrowserInterface
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookBefore
import com.tencent.smtt.sdk.WebView
import java.net.URL

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    override val key: String = "WebJsBridge"
    override val processes: Set<ActionProcess> = setOf(ActionProcess.TOOL)

    override fun onRun(app: Application, process: ActionProcess) {
        WebView::class.java.getMethod("loadUrl", String::class.java)
            .hookBefore { param ->
                val url = param.args[0] as String

                when {
                    isSettingPageUrl(url) -> handleSettingPageRedirect(param)
                    !PlatformTools.isHostWhitelisted(url) -> injectJavascriptInterface(param, app)
                }
            }
    }

    private fun isSettingPageUrl(url: String): Boolean =
        runCatching { URL(url).host == URL(SETTING_URL).host }.getOrDefault(false)

    private fun handleSettingPageRedirect(param: MethodHookParam) {
        param.result = Unit

        val context = QQInterfaces.topActivity
        runCatching {
            ModuleScope.launchMain {
                val latestLoader = System.getProperties()["tcqt.module_class_loader"] as? ClassLoader
                    ?: this.javaClass.classLoader
                val settingActivityClass = latestLoader.loadClass(SettingActivity::class.java.name)
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

    companion object {
        private const val SETTING_URL = "http://tcqt.qq.com/"
    }
}
