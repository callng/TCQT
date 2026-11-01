package com.owo233.tcqt.features.general

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.paramCount
import com.tencent.common.config.pad.DeviceType

@RegisterAction(processes = [ActionProcess.MSF])
object SwitchLoginMode : Feature(
    key = "switch_login_mode",
    name = "切换登录模式",
    desc = "在不改变UI的情况下以手机或平板模式登录账号，一个账号可以两处登录互不干扰。",
    uiOrder = 2,
) {

    private val loginType by intOption(
        settingKey = "type",
        name = "登录类型",
        defaultValue = 1,
        options = listOf("手机模式", "平板模式"),
    )

    override fun install() {
        loadOrThrow("com.tencent.common.config.pad.PadUtil")
            .declaredMethods.first {
                it.returnType == DeviceType::class.java && it.paramCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }.hookBefore { param ->
                when (loginType) {
                    1 -> param.result = DeviceType.PHONE
                    2 -> param.result = DeviceType.TABLET
                }
            }
    }
}
