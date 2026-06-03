package com.owo233.tcqt.hooks.func.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import androidx.core.graphics.drawable.IconCompat

internal data class ConversationTarget(
    val mainUin: Long,
    val mainName: String,
    val mainIcon: IconCompat?,
    val channel: NotifyChannel,
    val isGroupConversation: Boolean
) {
    val historyKey: String get() = "$channel+$mainUin"
}

internal data class RecentContactSnapshot(
    val chatType: Int,
    val abstractContent: Iterable<*>?,
    val sendMemberName: String?,
    val sendRemarkName: String?,
    val sendNickName: String?,
    val senderUin: Long,
    val peerUin: Long,
    val peerName: String?,
    val specialCareFlag: Byte,
    val msgBoxEvents: Any?
)

internal fun Any.toRecentContactSnapshot(): RecentContactSnapshot {
    return RecentContactSnapshot(
        chatType = intField("chatType") ?: 0,
        abstractContent = anyField("abstractContent") as? Iterable<*>,
        sendMemberName = stringField("sendMemberName"),
        sendRemarkName = stringField("sendRemarkName"),
        sendNickName = stringField("sendNickName"),
        senderUin = longField("senderUin") ?: 0L,
        peerUin = longField("peerUin") ?: 0L,
        peerName = stringField("peerName"),
        specialCareFlag = byteField("specialCareFlag") ?: 0.toByte(),
        msgBoxEvents = anyField("listOfSpecificEventTypeInfosInMsgBox")
    )
}

internal fun RecentContactSnapshot.isSupportedChat(): Boolean {
    return chatType == CHAT_TYPE_PRIVATE || chatType == CHAT_TYPE_GROUP
}

internal fun RecentContactSnapshot.extractContent(): String? {
    return abstractContent
        ?.joinToString(separator = "") { element ->
            element?.stringField("content") ?: "[未解析消息]"
        }
        ?.takeIf { it.isNotBlank() }
}

internal fun RecentContactSnapshot.senderName(): String? {
    return sendMemberName?.takeIf { it.isNotBlank() }
        ?: sendRemarkName?.takeIf { it.isNotBlank() }
        ?: sendNickName?.takeIf { it.isNotBlank() }
}

internal fun RecentContactSnapshot.isSpecialCare(): Boolean {
    return specialCareFlag == 1.toByte() ||
            msgBoxEvents.toString().contains("eventTypeInMsgBox=1006")
}

internal const val CHAT_TYPE_PRIVATE = 1
internal const val CHAT_TYPE_GROUP = 2
