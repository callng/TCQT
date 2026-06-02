package com.owo233.tcqt.hooks.func.notification

import android.app.Application
import android.content.Context
import android.content.Intent
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.ActionUiType
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.activity.NotificationChannelManagerActivity

@RegisterAction
class NotificationChannelManager : IAction {

    override val name: String get() = "通知渠道管理"
    override val desc: String get() = "管理应用内的通知渠道。"
    override val uiTab: String get() = "通知"
    override val key: String get() = "notification_channel_manager"
    override val uiType: ActionUiType get() = ActionUiType.ENTRY
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun onRun(app: Application, process: ActionProcess) = Unit

    override fun onUiClick(context: Context): Boolean {
        context.startActivity(
            Intent().apply {
                setClassName(HookEnv.hostAppPackageName, NotificationChannelManagerActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }
}
