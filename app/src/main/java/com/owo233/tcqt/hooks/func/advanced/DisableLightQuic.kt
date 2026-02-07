package com.owo233.tcqt.hooks.func.advanced

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.msfcore.MSFConfig
import com.tencent.mobileqq.msfcore.MSFKernel
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
        MSFKernel::class.java.findMethod {
            name = "setMSFConfig"
            paramTypes(int, MSFConfig::class.java)
        }.hookBeforeMethod { param ->
            val type = param.args[0] as Int
            if (type == 9) { // MSF_CONFIG_TYPE_NETWORK_CONFIGURE
                val config = param.args[1] as MSFNetworkConfig
                if (config.networkConnMode in setOf(4, 5)) {
                    config.networkConnMode = 1 // MSF_CONN_MODE_TCP
                    config.enableQuicRevertToTcpOnConnFail = true
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_LIGHT_QUIC

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
