package com.owo233.tcqt.features.hooks.func.basic

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.loadOrThrow
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.owo233.tcqt.foundation.utils.paramCount
import com.tencent.common.config.pad.DeviceType

@RegisterAction
@RegisterSetting(
    key = "switch_login_mode",
    name = "切换登录模式",
    type = SettingType.BOOLEAN,
    desc = "在不改变UI的情况下以手机或平板模式登录账号，一个账号可以两处登录互不干扰。",
    uiOrder = 2
)
@RegisterSetting(
    key = "switch_login_mode.type",
    name = "登录类型",
    type = SettingType.INT,
    defaultValue = "1",
    options = "手机模式|平板模式",
)
class SwitchLoginMode : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val loginType = GeneratedSettingList.getInt(GeneratedSettingList.SWITCH_LOGIN_MODE_TYPE)

        loadOrThrow("com.tencent.common.config.pad.PadUtil")
            .declaredMethods.first {
                it.returnType == DeviceType::class.java && it.paramCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }.hookAfterMethod { param ->
                when (loginType) {
                    1 -> param.result = DeviceType.PHONE
                    2 -> param.result = DeviceType.TABLET
                }
            }
    }

    override val key: String get() = GeneratedSettingList.SWITCH_LOGIN_MODE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}