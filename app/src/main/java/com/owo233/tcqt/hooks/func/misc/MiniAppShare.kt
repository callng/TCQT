package com.owo233.tcqt.hooks.func.misc

import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookBeforeMethod

@RegisterAction
@RegisterSetting(
    key = "mini_app_share",
    name = "修改小程序分享行为",
    type = SettingType.BOOLEAN,
    desc = "在小程序中调用分享时，如果取消分享，小程序也会收到分享成功的回调。",
    uiTab = "杂项"
)
class MiniAppShare : IAction {

    override val key: String
        get() = GeneratedSettingList.MINI_APP_SHARE

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.ALL) // 在MAIN或者MINI3又或者其他进程, 干脆ALL

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("eipc.EIPCClient").hookBeforeMethod(
            "callServer",
            String::class.java, String::class.java,
            Bundle::class.java, loadOrThrow("eipc.EIPCResultCallback")
        ) { param ->
            val module = param.args[0] as String
            if (module != "MiniMsgIPCServer") return@hookBeforeMethod

            // Log.d("MiniAppShare 当前进程处于: ${ProcUtil.currentProcName}")

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

        loadOrThrow("com.tencent.mobileqq.forward.ForwardBaseOption").hookBeforeMethod(
            "endForwardCallback",
            Boolean::class.javaPrimitiveType,
        ) { param ->
            param.args[0] = true
        }
    }
}
