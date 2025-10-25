package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.hooks.helper.LocalWebServer
import com.owo233.tcqt.internals.setting.TCQTJsInterface
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.hookAfterMethod
import com.tencent.smtt.sdk.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    internal lateinit var server: LocalWebServer

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (!::server.isInitialized) {
            val (host, port) = parseHostAndPort(TCQTSetting.getSettingUrl())

            // 使用 try-catch 更好
            try {
                server = LocalWebServer(host, port, TCQTSetting.getSettingHtml())
                server.start()
                Log.d("HTTP server started on $host:$port")
            } catch (e: java.io.IOException) {
                if (e is java.net.BindException || e.message?.contains("Address already in use") == true) {
                    // 已经有一个HTTP服务在运行
                    Log.d("Port $port already in use, skipping server start.")
                } else {
                    Log.e("Failed to start HTTP server", e)
                    return // 没必要继续往下执行了，因为服务器启动失败，下面做的一切都是无意义的
                }
            }
        }

        addWhiteList() // 添加host白名单

        WebView::class.java.declaredMethods
            .filter { it.name == "loadUrl" || it.name == "loadData" || it.name == "loadDataWithBaseURL"}
            .forEach {
                it.hookAfterMethod { param ->
                    val web = param.thisObject as WebView
                    val url = URL(web.url)
                    if (url.host == "tcqt.qq.com" || url.host == "tcqt.dev") {
                        web.loadUrl("http://${TCQTSetting.getSettingUrl()}")
                    } else if (url.host == TCQTSetting.getSettingUrl().substringBefore(":")) {
                        web.addJavascriptInterface(TCQTJsInterface(ctx), "TCQT")
                    }
                }
            }
    }

    private fun addWhiteList() {
        XpClassLoader.load("com.tencent.mobileqq.qmmkv.MMKVOptionEntity")
            ?.hookAfterMethod(
                "decodeString",
                String::class.java,
                String::class.java
            ) {
                val key = it.args[0] as? String ?: return@hookAfterMethod
                if (key == "207_2849" || key.startsWith("207_")) {
                    val result = it.result as? String ?: return@hookAfterMethod
                    val json = JSONObject(result)
                    val hosts = listOf(parseHostAndPort(TCQTSetting.getSettingUrl()).first)

                    addHosts(json, "whiteList", hosts)
                    addHosts(json, "whiteListv2", hosts)
                    addHosts(json, "kbWhiteList", hosts)

                    it.result = json.toString()
                }
            }
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

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
