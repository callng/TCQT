package com.owo233.tcqt.features.hooks.func.advanced

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.load
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.tencent.mobileqq.msfcore.MSFConfig
import com.tencent.mobileqq.msfcore.MSFNetworkConfig

@RegisterAction
@RegisterSetting(
    key = "disable_light_quic",
    name = "禁用QUIC",
    type = SettingType.BOOLEAN,
    desc = "不允许MSF使用QUIC，强制它使用TCP。",
    uiTab = "高级"
)
class DisableLightQuic : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        load("com.tencent.mobileqq.msfcore.MSFKernel")!!
            .hookBeforeMethod(
                "setMSFConfig",
                Int::class.javaPrimitiveType,
                MSFConfig::class.java
            ) { param ->
                val type = param.args[0] as Int
                if (type == 9) {
                    val config = param.args[1] as MSFNetworkConfig
                    config.networkConnMode = 1 // revert to Tcp from quic
                }
            }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_LIGHT_QUIC

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
