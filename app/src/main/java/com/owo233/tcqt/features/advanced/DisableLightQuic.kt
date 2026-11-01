package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod
import com.tencent.mobileqq.msfcore.MSFConfig
import com.tencent.mobileqq.msfcore.MSFKernel
import com.tencent.mobileqq.msfcore.MSFNetworkConfig

@RegisterAction(processes = [ActionProcess.MSF])
object DisableLightQuic : Feature(
    key = "disable_light_quic",
    name = "禁用QUIC",
    desc = "不允许MSF使用QUIC，强制它使用TCP。",
) {


    override fun install() {
        MSFKernel::class.java.findMethod {
            name = "setMSFConfig"
            paramTypes(int, MSFConfig::class.java)
        }.hookBefore { param ->
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

}
