package com.owo233.tcqt.hooks.func.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi

enum class NotifyChannel {
    FRIEND,
    FRIEND_SPECIAL,
    GROUP,
    QZONE
}

internal fun NotifyChannel.channelId(): String = when (this) {
    NotifyChannel.FRIEND -> "QQ_Friend"
    NotifyChannel.FRIEND_SPECIAL -> "QQ_Friend_Special"
    NotifyChannel.GROUP -> "QQ_Group"
    NotifyChannel.QZONE -> "QQ_Zone"
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun createQAuxNotificationChannels(notificationManager: NotificationManager) {
    val channels = listOf(
        buildChannel(NotifyChannel.FRIEND, "联系人消息", "QQ 私聊消息通知", NotificationManager.IMPORTANCE_HIGH),
        buildChannel(NotifyChannel.FRIEND_SPECIAL, "特别关心消息", "QQ 特别关心好友私聊消息通知", NotificationManager.IMPORTANCE_HIGH),
        buildChannel(NotifyChannel.GROUP, "群消息", "QQ 群消息通知", NotificationManager.IMPORTANCE_HIGH),
        buildChannel(NotifyChannel.QZONE, "空间动态", "QQ 空间动态通知", NotificationManager.IMPORTANCE_DEFAULT)
    )

    if (channels.any { notificationManager.getNotificationChannel(it.id) == null }) {
        notificationManager.createNotificationChannels(channels)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun buildChannel(
    channel: NotifyChannel,
    name: String,
    desc: String,
    importance: Int
): NotificationChannel {
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    return NotificationChannel(channel.channelId(), name, importance).apply {
        group = "qq_evolution"
        description = desc
        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
        enableVibration(true)
        enableLights(true)
    }
}
