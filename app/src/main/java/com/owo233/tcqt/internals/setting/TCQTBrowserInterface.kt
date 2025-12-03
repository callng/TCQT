package com.owo233.tcqt.internals.setting

import android.content.Context
import android.webkit.JavascriptInterface
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.utils.Log

class TCQTBrowserInterface(private val ctx: Context) {

    @JavascriptInterface
    fun getSetting(key: String): String {
        return runCatching {
            val existingSetting = TCQTSetting.settingMap[key]
            val value: Any? = if (existingSetting != null) {
                when (existingSetting.type) {
                    TCQTSetting.SettingType.BOOLEAN -> TCQTSetting.getValue<Boolean>(key)
                    TCQTSetting.SettingType.INT -> TCQTSetting.getValue<Int>(key)
                    TCQTSetting.SettingType.STRING -> TCQTSetting.getValue<String>(key)
                }
            } else {
                TCQTSetting.getValue<String>(key)
                    ?: TCQTSetting.getValue<Boolean>(key)
                    ?: TCQTSetting.getValue<Int>(key)
            }

            // 如果值为null,返回空对象
            if (value == null) {
                // Log.w("getSetting: key=$key, value is null, returning {}")
                return "{}"
            }

            val result = mapOf("value" to value).json.toString()

            result

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
    fun saveValueI(key: String, value: Int) {
        TCQTSetting.setValue(key, value)
    }
}
