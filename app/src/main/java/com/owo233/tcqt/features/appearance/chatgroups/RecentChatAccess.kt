package com.owo233.tcqt.features.appearance.chatgroups

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo

/** Native contact fields are shared by QQ 9.3.x; older lists have no fold wrapper. */
internal class RecentChatAccess(private val itemClass: Class<*>, foldClass: Class<*>?) {
    private val contact = itemClass.declaredFields.single { it.type == RecentContactInfo::class.java }
        .apply { isAccessible = true }
    private val folded = foldClass?.declaredFields?.single { it.type == List::class.java }
        ?.apply { isAccessible = true }

    fun isConversation(item: Any): Boolean = itemClass.isInstance(item)

    fun describe(item: Any): ChatSummary? {
        if (!itemClass.isInstance(item)) return null
        val info = contact.get(item) as RecentContactInfo
        val guild = info.guildContactInfo
        val guildUnread = guild?.unreadCntInfo?.showUnreadCnt
        return ChatSummary(
            key = "${info.chatType}:${info.contactId}",
            title = info.remark.orEmpty().ifBlank { info.peerName.orEmpty() }
                .ifBlank { guild?.guildName.orEmpty() }
                .ifBlank { info.peerUid.orEmpty() }.ifBlank { info.contactId.toString() },
            type = info.chatType,
            unread = info.unreadCnt > 0 || info.unreadFlag == 1L ||
                (info.chatType == 16 && guildUnread != null && guildUnread.type != 4 && guildUnread.cnt > 0),
            detail = listOfNotNull(when (info.chatType) {
                1 -> "好友"
                2 -> "群聊"
                16, 60 -> "频道"
                100 -> "临时会话"
                else -> "其他会话"
            }, info.peerUin.takeIf { it > 0 }?.toString()).joinToString(" · "),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun foldedItems(item: Any): List<Any>? =
        if (folded != null && folded.declaringClass.isInstance(item)) folded.get(item) as List<Any> else null

    fun catalog(items: List<Any>): List<ChatSummary> = buildMap {
        fun append(rows: List<Any>) {
            for (item in rows) {
                val children = foldedItems(item)
                if (children != null) append(children)
                else describe(item)?.let { put(it.key, it) }
            }
        }
        append(items)
    }.values.toList()
}
