package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.mobileqq.msfcore.MSFConfig
import com.tencent.mobileqq.msfcore.MSFNetworkConfig

@RegisterAction
@RegisterSetting(
    key = "disable_light_quic",
    name = "禁用QUIC",
    type = SettingType.BOOLEAN,
    desc = "不允许MSF使用QUIC，强制它使用TCP。",
    uiTab = "高级",
    uiOrder = 108
)
class DisableLightQuic : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mobileqq.msfcore.MSFKernel")!!
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
