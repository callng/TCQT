package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.hooks.helper.LocalWebServer
import com.owo233.tcqt.internals.setting.TCQTJsInterface
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.tencent.smtt.sdk.WebView
import de.robv.android.xposed.XposedBridge
import org.json.JSONArray
import org.json.JSONObject
import java.net.Socket
import java.net.URL

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    internal lateinit var server: LocalWebServer

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (!::server.isInitialized) {
            val (host, port) = parseHostAndPort(TCQTSetting.settingUrl)
            if (!isPortInUse(host, port)) {
                server = LocalWebServer(host, port, TCQTSetting.settingHtml)
                server.start()
            }
        }

        addWhiteList() // 添加host白名单

        val onLoad = afterHook {
            val web = it.thisObject as WebView
            val url = URL(web.url)
            if (url.host == "tcqt.qq.com" || url.host == "tcqt.dev") {
                web.loadUrl("http://${TCQTSetting.settingUrl}")
            } else if (url.host == TCQTSetting.settingUrl.substringBefore(":")) {
                web.addJavascriptInterface(TCQTJsInterface(ctx), "TCQT")
            }
        }
        WebView::class.java.declaredMethods
            .filter { it.name == "loadUrl" || it.name == "loadData" || it.name == "loadDataWithBaseURL"}
            .forEach { XposedBridge.hookMethod(it, onLoad) }
    }

    private fun addWhiteList() {
        XpClassLoader.load("com.tencent.mobileqq.qmmkv.MMKVOptionEntity")
            ?.hookMethod("decodeString", afterHook {
                val key = it.args[0] as? String ?: return@afterHook
                if (key == "207_2849" || key.startsWith("207_")) {
                    val result = it.result as? String ?: return@afterHook
                    val json = JSONObject(result)
                    val hosts = listOf("127.0.0.1", "localhost")

                    addHosts(json, "whiteList", hosts)
                    addHosts(json, "whiteListv2", hosts)
                    addHosts(json, "kbWhiteList", hosts)

                    it.result = json.toString()
                }
            })
    }

    private fun addHosts(json: JSONObject, key: String, hosts: List<String>) {
        val array = if (json.has(key)) {
            json.getJSONArray(key)
        } else {
            val newArr = JSONArray()
            json.put(key, newArr)
            newArr
        }

        val existing = (0 until array.length())
            .map{ array.getString(it) }
            .toMutableSet()

        for (host in hosts) {
            if (existing.add(host)) {
                array.put(host)
            }
        }
    }

    private fun parseHostAndPort(url: String): Pair<String, Int> {
        val parts = url.split(":")
        require(parts.size == 2) { "Invalid settingUrl format" }
        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: error("Port is not a valid number")
        return host to port
    }

    private fun isPortInUse(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
