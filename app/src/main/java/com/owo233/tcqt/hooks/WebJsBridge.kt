package com.owo233.tcqt.hooks

import android.content.Context
import android.webkit.JavascriptInterface
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.hooks.helper.LocalWebServer
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.Toasts
import com.tencent.smtt.sdk.WebView
import de.robv.android.xposed.XposedBridge
import mqq.app.MobileQQ
import java.net.URL

@RegisterAction
class WebJsBridge: AlwaysRunAction() {

    internal lateinit var server: LocalWebServer

    override fun onRun(ctx: Context) {
        val onLoad = afterHook {
            val web = it.thisObject as WebView
            val url = URL(web.url)
            if (url.host == "tcqt.qq.com" || url.host == "tcqt.dev") {
                web.loadUrl("http://${TCQTSetting.settingUrl}")
            } else if (url.host == TCQTSetting.settingUrl.substringBefore(":")) {
                if (!::server.isInitialized) {
                    val (host, port) = parseHostAndPort(TCQTSetting.settingUrl)
                    server = LocalWebServer(host, port, TCQTSetting.settingHtml)
                    server.start()
                }
                web.addJavascriptInterface(TCQTJsBridge, "TCQT")
            }
        }
        WebView::class.java.declaredMethods
            .filter { it.name == "loadUrl" || it.name == "loadData" || it.name == "loadDataWithBaseURL"}
            .forEach { XposedBridge.hookMethod(it, onLoad) }
    }

    private fun parseHostAndPort(url: String): Pair<String, Int> {
        val parts = url.split(":")
        require(parts.size == 2) { "Invalid settingUrl format" }
        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: error("Port is not a valid number")
        return host to port
    }

    companion object TCQTJsBridge {
        @JavascriptInterface
        fun getQQVersion(): String = "${PlatformTools.getQQVersion()}(${PlatformTools.getQQVersionCode()})"

        @JavascriptInterface
        fun getModuleVersion(): String = "${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE})"

        @JavascriptInterface
        fun toast(str: String) {
            Toasts.success(MobileQQ.getContext(), str)
        }

        @JavascriptInterface
        fun getSetting(key: String): String {
            val setting = TCQTSetting.getSetting<Any>(key)
            val value = when (setting.type) {
                TCQTSetting.SettingType.BOOLEAN -> setting.getValue(setting, null) as  Boolean
                TCQTSetting.SettingType.INT -> setting.getValue(setting, null) as  Int
                TCQTSetting.SettingType.STRING -> setting.getValue(setting, null) as String
            }
            return mapOf(
                "value" to value
            ).json.toString()
        }

        @JavascriptInterface
        fun setSettingString(key: String, value: String) {
            val setting = TCQTSetting.getSetting<Any>(key)
            if (setting.type == TCQTSetting.SettingType.BOOLEAN) {
                return // 不支持 boolean
            }
            setting.setValue(setting, null, value)
        }

        @JavascriptInterface
        fun setSettingBoolean(key: String, value: Boolean) {
            val setting = TCQTSetting.getSetting<Any>(key)
            setting.setValue(setting, null, value)
        }
    }

    override val name: String get() = "WebJsBridge"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
