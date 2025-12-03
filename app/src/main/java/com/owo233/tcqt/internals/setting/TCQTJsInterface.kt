package com.owo233.tcqt.internals.setting

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.webkit.JavascriptInterface
import androidx.core.net.toUri
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.generated.GeneratedFeaturesData
import com.owo233.tcqt.hooks.ModuleCommand
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.Toasts
import com.tencent.mobileqq.vas.theme.api.ThemeUtil

class TCQTJsInterface(private val ctx: Context) {
    @JavascriptInterface
    fun getHostChannel(): String = PlatformTools.getHostChannel(ctx)

    @JavascriptInterface
    fun getHostVersion(): String = "${PlatformTools.getHostVersion(ctx)}(${PlatformTools.getHostVersionCode(ctx)})"

    @JavascriptInterface
    fun getHostName(): String = HookEnv.appName

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

    @JavascriptInterface
    fun openUrlInDefaultBrowser(url: String) {
        if (!openTelegramChannel()) {
            runCatching {
                if (!url.contains(TCQTBuild.OPEN_SOURCE)) {
                    Toasts.error(ctx, "尝试打开不支持的链接!")
                    Log.e("尝试打开不受支持的链接: $url !!!")
                    return
                }
                val uri = url.toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            }.onFailure { _ ->
                Toasts.error(ctx, "Failed to open url: $url")
                ctx.copyToClipboard(url, false)
                Toasts.info(ctx, "Url地址已复制到剪贴板,请手动访问.")
            }
        }
    }

    @JavascriptInterface
    fun isDarkModeBySystem(): Boolean {
        val mode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    @JavascriptInterface
    fun isDarkModeByHost(): Boolean {
        return ThemeUtil.isInNightMode(null) // isInNightMode方法可能会被移除
    }

    @JavascriptInterface
    fun openTelegramChannel(): Boolean {
        val tgIntent = Intent(Intent.ACTION_VIEW).apply {
            data = "tg://resolve?domain=${TCQTBuild.TG_GROUP}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            ctx.startActivity(tgIntent)
            return true
        } catch (_: ActivityNotFoundException) {
            return false
        } catch (_: Exception) {
            return false
        }
    }
}
