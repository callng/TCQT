package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.msfcore.MSFConfig
import com.tencent.mobileqq.msfcore.MSFKernel
import com.tencent.mobileqq.msfcore.MSFNetworkConfig

@RegisterAction
class DisableLightQuic : IAction {

    override val name: String get() = "禁用QUIC"
    override val desc: String get() = "不允许MSF使用QUIC，强制它使用TCP。"
    override val uiTab: String get() = "高级"

    override fun onRun(app: Application, process: ActionProcess) {
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

    override val key: String get() = "disable_light_quic"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
