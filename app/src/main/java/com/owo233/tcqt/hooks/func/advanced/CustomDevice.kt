package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.reflect.getMethods

@RegisterAction
class CustomDevice : IAction {

    override val name: String get() = "自定义设备信息"
    override val desc: String get() = "自定义宿主获取的[device, model, manufacturer]，如果本功能未启用且某个值未填写，则使用当前设备信息填充。"
    override val uiTab: String get() = "高级"
    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting(
                "custom_device.string.device",
                "设备代号",
                "",
                "",
                "填写device内容, e.g: ingres",
                false
            ),
            StringSetting(
                "custom_device.string.model",
                "设备型号",
                "",
                "",
                "填写model内容, e.g: 21121210C",
                false
            ),
            StringSetting(
                "custom_device.string.manufacturer",
                "设备制造商",
                "",
                "",
                "填写manufacturer内容, e.g: Xiaomi",
                false
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) {
        load("android.os.SystemProperties")!!
            .getMethods(false)
            .filter { it.name == "get" }
            .forEach { method ->
                method.hookBefore { param ->
                    val key = param.args.getOrNull(0) as? String ?: return@hookBefore

                    val replacement = when (key) {
                        DEVICE_KEY -> device
                        MODEL_KEY -> model
                        MANUFACTURER_KEY -> manufacturer
                        else -> null
                    }?.takeIf { it.isNotBlank() }

                    replacement?.let { param.result = it }
                }
            }

        // 干缓存
        val deviceInfoClz = load(
            "com.tencent.qmethod.pandoraex.monitor.DeviceInfoMonitor"
        ) ?: error("DeviceInfoMonitor is null")
        deviceInfoClz.hookMethodReplace("getModel") { param ->
            model.takeIf { it.isNotBlank() } ?: param.invokeOriginal()
        }
    }

    override val key: String get() = "custom_device"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

    override fun canRun(): Boolean {
        val isEnabled = TCQTSetting.getBoolean(key)

        if (!isEnabled) {
            if (device.isBlank()) {
                TCQTSetting.setString(
                    "custom_device.string.device",
                    Build.DEVICE
                )
            }
            if (model.isBlank()) {
                TCQTSetting.setString(
                    "custom_device.string.model",
                    Build.MODEL
                )
            }
            if (manufacturer.isBlank()) {
                TCQTSetting.setString(
                    "custom_device.string.manufacturer",
                    Build.MANUFACTURER
                )
            }
        }

        return isEnabled
    }

    companion object {
        const val DEVICE_KEY = "ro.product.device"
        const val MODEL_KEY = "ro.product.model"
        const val MANUFACTURER_KEY = "ro.product.manufacturer"

        val device by lazy {
            TCQTSetting.getString(
                "custom_device.string.device"
            )
        }
        val model by lazy {
            TCQTSetting.getString(
                "custom_device.string.model"
            )
        }
        val manufacturer by lazy {
            TCQTSetting.getString(
                "custom_device.string.manufacturer"
            )
        }
    }
}
