package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.internals.setting.LocalWebServer
import com.owo233.tcqt.internals.setting.TCQTJsInterface
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hookAfterMethod
import com.tencent.smtt.sdk.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

@RegisterAction
class WebJsBridge : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        addWhiteList() // 添加host白名单

        WebView::class.java.declaredMethods
            .filter { it.name == "loadUrl" || it.name == "loadData" || it.name == "loadDataWithBaseURL"}
            .forEach { method ->
                method.hookAfterMethod { param ->
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
                    val hosts = listOf(LocalWebServer.settingUrlPair.first)

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

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
