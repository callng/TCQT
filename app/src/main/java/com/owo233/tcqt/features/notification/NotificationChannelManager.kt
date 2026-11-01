package com.owo233.tcqt.features.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents

@RegisterAction(processes = [ActionProcess.MAIN])
object NotificationChannelManager : Feature(
    key = "notification_channel_manager",
    name = "通知渠道管理",
    desc = "管理应用内的通知渠道。",
    uiType = ActionUiType.ENTRY,
) {

    override fun install() = Unit

    override fun onUiClick(context: Context): Boolean {
        context.startActivity(
            Intent().apply {
                setClassName(
                    HookEnv.hostAppPackageName,
                    ModuleComponents.NOTIFICATION_CHANNEL_ACTIVITY
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }
}
