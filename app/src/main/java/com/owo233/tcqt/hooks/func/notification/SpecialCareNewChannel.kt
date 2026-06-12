package com.owo233.tcqt.hooks.func.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore

@RegisterAction
class SpecialCareNewChannel : IAction {

    override val name: String get() = "特别关心通知单独分组"
    override val desc: String get() = "将特别关心发送的消息通知移动到单独的通知渠道"
    override val uiTab: String get() = "通知"
    override val key: String get() = "special_care_new_channel"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)

    override fun onRun(app: Application, process: ActionProcess) {
        NotificationManager::class.java.declaredMethods
            .filter { it.name == "notify" && it.parameterTypes.lastOrNull() == Notification::class.java }
            .forEach { method ->
                method.isAccessible = true
                method.hookBefore { param ->
                    val notification = param.args.lastOrNull() as? Notification ?: return@hookBefore
                    val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    if (!title.contains("[特别关心]")) return@hookBefore

                    ensureSpecialCareChannel()
                    notification.setFieldValue("mChannelId", CHANNEL_ID_SPECIALLY_CARE)
                    param.args[param.args.lastIndex] = notification
                }
            }
    }

    private fun ensureSpecialCareChannel() {
        val notificationManager = HookEnv.application.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID_SPECIALLY_CARE) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID_SPECIALLY_CARE,
            "特别关心",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID_SPECIALLY_CARE = "CHANNEL_ID_SPECIALLY_CARE"
    }
}
