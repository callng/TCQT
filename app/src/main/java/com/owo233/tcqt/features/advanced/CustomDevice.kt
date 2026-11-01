package com.owo233.tcqt.features.advanced

import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.reflect.getMethods

@RegisterAction(processes = [ActionProcess.ALL])
object CustomDevice : Feature(
    key = "custom_device",
    name = "自定义设备信息",
    desc = "自定义宿主获取的[device, model, manufacturer]，如果本功能未启用且某个值未填写，则使用当前设备信息填充。",
) {

    private var device by stringOption(
        settingKey = "string.device",
        name = "设备代号",
        placeholder = "填写device内容, e.g: ingres",
    )

    private var model by stringOption(
        settingKey = "string.model",
        name = "设备型号",
        placeholder = "填写model内容, e.g: 21121210C",
    )

    private var manufacturer by stringOption(
        settingKey = "string.manufacturer",
        name = "设备制造商",
        placeholder = "填写manufacturer内容, e.g: Xiaomi",
    )

    override fun install() {
        fillDefaultDeviceInfo()
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

    private fun fillDefaultDeviceInfo() {
        if (device.isBlank()) device = Build.DEVICE
        if (model.isBlank()) model = Build.MODEL
        if (manufacturer.isBlank()) manufacturer = Build.MANUFACTURER
    }

    private const val DEVICE_KEY = "ro.product.device"
    private const val MODEL_KEY = "ro.product.model"
    private const val MANUFACTURER_KEY = "ro.product.manufacturer"
}
