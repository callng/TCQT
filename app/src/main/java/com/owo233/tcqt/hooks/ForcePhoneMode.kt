package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hookAfterMethod
import com.tencent.common.config.pad.DeviceType

@RegisterAction
@RegisterSetting(
    key = "force_phone_mode",
    name = "强制手机模式",
    type = SettingType.BOOLEAN,
    desc = "和「强制平板模式」功能一样的作用，不同的是，「强制手机模式」优先级比「强制平板模式」低，两者都启用的情况下优先生效后者。",
    uiOrder = 9
)
class ForcePhoneMode : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        load("com.tencent.common.config.pad.PadUtil")
            ?.declaredMethods?.first {
                it.returnType == DeviceType::class.java && it.parameterCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }?.hookAfterMethod{
                it.result = DeviceType.PHONE
            }
    }

    override fun canRun(): Boolean {
        return GeneratedSettingList.getBoolean(key) &&
                !GeneratedSettingList.getBoolean(GeneratedSettingList.FORCE_TABLET_MODE)
    }

    override val key: String get() = GeneratedSettingList.FORCE_PHONE_MODE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
