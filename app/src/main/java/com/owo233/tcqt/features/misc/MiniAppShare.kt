package com.owo233.tcqt.features.misc

import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodBefore

@RegisterAction
object MiniAppShare : Feature(
    key = "mini_app_share",
    name = "修改小程序分享行为",
    desc = "在小程序中调用分享时，如果取消分享，小程序也会收到分享成功的回调。",
    processes = setOf(ActionProcess.ALL),
) {

    override fun install() {
        loadOrThrow("eipc.EIPCClient").hookMethodBefore(
            "callServer",
            String::class.java, String::class.java,
            Bundle::class.java, loadOrThrow("eipc.EIPCResultCallback")
        ) { param ->
            val module = param.args[0] as String
            if (module != "MiniMsgIPCServer") return@hookMethodBefore

            when (param.args[1] as String) {
                "cmd_mini_share_fail" -> {
                    param.args[1] = "cmd_mini_share_suc"
                }

                "cmd_mini_report_event" -> {
                    (param.args[2] as Bundle)
                        .takeIf { it.getString("key_mini_report_event_reserves2") == "fail" }
                        ?.putString("key_mini_report_event_reserves2", "success")
                }
            }
        }

        loadOrThrow("com.tencent.mobileqq.forward.ForwardBaseOption").hookMethodBefore(
            "endForwardCallback",
            Boolean::class.javaPrimitiveType,
        ) { param ->
            param.args[0] = true
        }
    }
}
