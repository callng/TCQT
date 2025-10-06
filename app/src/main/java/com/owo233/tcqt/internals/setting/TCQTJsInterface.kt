package com.owo233.tcqt.internals.setting

import android.content.Context
import android.webkit.JavascriptInterface
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.generated.GeneratedFeaturesData
import com.owo233.tcqt.hooks.ModuleCommand
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.Toasts

class TCQTJsInterface(private val ctx: Context) {
    @JavascriptInterface
    fun getHostChannel(): String = PlatformTools.getHostChannel(ctx)

    @JavascriptInterface
    fun getHostVersion(): String = "${PlatformTools.getHostVersion(ctx)}(${PlatformTools.getHostVersionCode(ctx)})"

    @JavascriptInterface
    fun getHostName(): String = hostInfo.hostName

    @JavascriptInterface
    fun getModuleVersion(): String = TCQTBuild.VER_NAME

    @JavascriptInterface
    fun getModuleName(): String = TCQTBuild.APP_NAME

    @JavascriptInterface
    fun toast(str: String) {
        Toasts.success(ctx, str)
    }

    @JavascriptInterface
    fun exitApp() {
        ModuleCommand.sendCommand(ctx, "exitApp")
    }

    @JavascriptInterface
    fun clear() {
        ModuleCommand.sendCommand(ctx, "config_clear")
    }

    @JavascriptInterface
    fun getSetting(key: String): String {
        return runCatching {
            val value: Any = TCQTSetting.getValue<Any>(key) ?: return "{}"

            val result = when (value) {
                is Boolean, Int, String -> value
                else -> value.toString()
            }

            mapOf("value" to result).json.toString()

        }.onFailure {
            Log.e("Failed to get setting for key: $key", it)
        }.getOrNull() ?: "{}"
    }

    @JavascriptInterface
    fun saveValueS(key: String, value: String) {
        TCQTSetting.setValue(key, value)
    }

    @JavascriptInterface
    fun saveValueB(key: String, value: Boolean) {
        TCQTSetting.setValue(key, value)
    }

    @JavascriptInterface
    fun getEnabledActionCount(): Int {
        return ActionManager.getEnabledActionCount()
    }

    @JavascriptInterface
    fun getDisabledActionCount(): Int {
        return ActionManager.getDisabledActionCount()
    }

    @JavascriptInterface
    fun getFeaturesConfig(): String {
        return GeneratedFeaturesData.toJsonString()
    }
}
