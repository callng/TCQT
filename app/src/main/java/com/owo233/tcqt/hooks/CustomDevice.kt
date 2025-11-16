package com.owo233.tcqt.hooks

import android.content.Context
import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.invokeOriginalMethod
import com.owo233.tcqt.utils.replaceMethod

@RegisterAction
@RegisterSetting(
    key = "custom_device",
    name = "自定义设备信息",
    type = SettingType.BOOLEAN,
    desc = "自定义宿主获取的[device, model, manufacturer]，如果本功能未启用且某个值未填写，则使用当前设备信息填充。",
    hasTextAreas = true,
    uiTab = "高级",
    uiOrder = 101
)
@RegisterSetting(
    key = "custom_device.string.device",
    name = "设备代号",
    type = SettingType.STRING,
    textAreaPlaceholder = "填写device内容, e.g: ingres"
)
@RegisterSetting(
    key = "custom_device.string.model",
    name = "设备型号",
    type = SettingType.STRING,
    textAreaPlaceholder = "填写model内容, e.g: 21121210C"
)
@RegisterSetting(
    key = "custom_device.string.manufacturer",
    name = "设备制造商",
    type = SettingType.STRING,
    textAreaPlaceholder = "填写manufacturer内容, e.g: Xiaomi"
)
class CustomDevice : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("android.os.SystemProperties")!!
            .getMethods(false)
            .filter { it.name == "get" }
            .forEach { method ->
                method.hookBeforeMethod {param ->
                    val key = param.args.getOrNull(0) as? String ?: return@hookBeforeMethod

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
        val deviceInfoClz = XpClassLoader.load(
            "com.tencent.qmethod.pandoraex.monitor.DeviceInfoMonitor"
        ) ?: error("DeviceInfoMonitor is null")
        deviceInfoClz.replaceMethod("getModel") { param ->
            model.takeIf { it.isNotBlank() } ?: param.invokeOriginalMethod()
        }
    }

    override val key: String get() = GeneratedSettingList.CUSTOM_DEVICE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

    override fun canRun(): Boolean {
        val isEnabled = GeneratedSettingList.getBoolean(key)

        if (!isEnabled) {
            if (device.isBlank()) {
                GeneratedSettingList.setString(
                    GeneratedSettingList.CUSTOM_DEVICE_STRING_DEVICE,
                    Build.DEVICE)
            }
            if (model.isBlank()) {
                GeneratedSettingList.setString(
                    GeneratedSettingList.CUSTOM_DEVICE_STRING_MODEL,
                    Build.MODEL)
            }
            if (manufacturer.isBlank()) {
                GeneratedSettingList.setString(
                    GeneratedSettingList.CUSTOM_DEVICE_STRING_MANUFACTURER,
                    Build.MANUFACTURER)
            }
        }

        return isEnabled
    }

    companion object {
        const val DEVICE_KEY = "ro.product.device"
        const val MODEL_KEY = "ro.product.model"
        const val MANUFACTURER_KEY = "ro.product.manufacturer"

        val device by lazy { GeneratedSettingList.getString(
            GeneratedSettingList.CUSTOM_DEVICE_STRING_DEVICE)
        }
        val model by lazy { GeneratedSettingList.getString(
            GeneratedSettingList.CUSTOM_DEVICE_STRING_MODEL)
        }
        val manufacturer by lazy { GeneratedSettingList.getString(
            GeneratedSettingList.CUSTOM_DEVICE_STRING_MANUFACTURER)
        }
    }
}
