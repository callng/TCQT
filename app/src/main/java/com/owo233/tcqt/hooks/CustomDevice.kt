package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.invokeOriginal
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.utils.logE

@RegisterAction
class CustomDevice: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("android.os.SystemProperties")!!
            .getMethods(false)
            .filter { it.name == "get" }
            .forEach { method ->
                method.hookMethod(beforeHook { param ->
                    val key = param.args.getOrNull(0) as? String ?: return@beforeHook

                    val replacement = when (key) {
                        DEVICE_KEY -> device
                        MODEL_KEY -> model
                        MANUFACTURER_KEY -> manufacturer
                        else -> null
                    }?.takeIf { it.isNotBlank() }

                    replacement?.let { param.result = it }
                })
            }

        // 干缓存
        val deviceInfoClz = XpClassLoader.load(
            "com.tencent.qmethod.pandoraex.monitor.DeviceInfoMonitor"
        ) ?: error("DeviceInfoMonitor is null")
        deviceInfoClz.hookMethod("getModel", replaceHook { param ->
            model.takeIf { it.isNotBlank() } ?: param.invokeOriginal()
        })
    }

    override val name: String get() = "自定义设备信息"

    override val key: String get() = TCQTSetting.CUSTOM_DEVICE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

    companion object {
        const val DEVICE_KEY = "ro.product.device"
        const val MODEL_KEY = "ro.product.model"
        const val MANUFACTURER_KEY = "ro.product.manufacturer"

        private fun getSettingValue(settingKey: String): String {
            return try {
                (TCQTSetting.getSetting<Any>(settingKey).getValue(this, null) as? String)
                    .orEmpty()
                    .trim()
            } catch (e: Exception) {
                logE(msg = "getSettingValue error", cause = e)
                ""
            }
        }

        val device by lazy { getSettingValue(TCQTSetting.CUSTOM_DEVICE_STRING_DEVICE) }
        val model by lazy { getSettingValue(TCQTSetting.CUSTOM_DEVICE_STRING_MODEL) }
        val manufacturer by lazy { getSettingValue(TCQTSetting.CUSTOM_DEVICE_STRING_MANUFACTURER) }
    }
}
