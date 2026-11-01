package com.owo233.tcqt.features.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.hook.hookBefore

@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MSF])
object SpecialCareNewChannel : Feature(
    key = "special_care_new_channel",
    name = "特别关心通知单独分组",
    desc = "将特别关心发送的消息通知移动到单独的通知渠道",
) {

    override fun install() {
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


    private const val CHANNEL_ID_SPECIALLY_CARE = "CHANNEL_ID_SPECIALLY_CARE"
}
