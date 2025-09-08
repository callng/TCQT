package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.invokeOriginal
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.utils.getMethods

@RegisterAction
@RegisterSetting(
    key = "custom_device",
    name = "自定义设备信息",
    type = SettingType.BOOLEAN,
    desc = "自定义宿主获取的[device, model, manufacturer]，如果按照下面例子填写每个编辑框内容，则宿主会识别设备为[Redmi K50 电竞版]",
    isRedMark = true,
    hasTextAreas = true,
    uiOrder = 2
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

    override val key: String get() = GeneratedSettingList.CUSTOM_DEVICE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

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
