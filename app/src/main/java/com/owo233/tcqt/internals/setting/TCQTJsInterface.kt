package com.owo233.tcqt.internals.setting

import android.content.Context
import android.webkit.JavascriptInterface
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.hooks.ModuleCommand
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.Toasts
import com.owo233.tcqt.utils.hostInfo

class TCQTJsInterface(private val ctx: Context) {
    @JavascriptInterface
    fun getQQVersion(): String = "${PlatformTools.getQQVersion(ctx)}(${PlatformTools.getQQVersionCode(ctx)})"

    @JavascriptInterface
    fun getHostName(): String = hostInfo.hostName

    @JavascriptInterface
    fun getModuleVersion(): String = "${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE})"

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
