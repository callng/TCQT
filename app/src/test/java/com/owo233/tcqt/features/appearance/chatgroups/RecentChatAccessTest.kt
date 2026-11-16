package com.owo233.tcqt.features.appearance.chatgroups

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentChatAccessTest {
    private class RecentItem(val contact: RecentContactInfo)
    private class FoldItem(val items: List<Any>)
    private val access = RecentChatAccess(RecentItem::class.java, FoldItem::class.java)

    private fun recent(id: Long, type: Int, unread: Long) = RecentItem(RecentContactInfo().apply {
        contactId = id
        chatType = type
        peerName = "会话$id"
        peerUid = if (type == 1) "u_$id" else id.toString()
        unreadCnt = unread
    })

    @Test
    fun fullCatalogUnfoldsAndDeduplicatesChatsBeforeCountingBadges() {
        val group = recent(20, 2, 100)
        val friend = recent(10, 1, 0)
        val chats = access.catalog(listOf(Any(), FoldItem(listOf(friend, group)), group))
        assertEquals(listOf("1:10", "2:20"), chats.map { it.key })
        assertEquals(listOf(false, true), chats.map { it.unread })
        assertEquals(1, chatGroupTabs(ChatCategory.entries, emptyList(), emptyList(), chats)
            .first { it.id == "all" }.unreadCount)
    }

    @Test
    fun manualUnreadFlagCountsAndHostReadUpdatesClearIt() {
        val chat = recent(20, 2, 0)
        chat.contact.unreadFlag = 1
        assertEquals(true, access.describe(chat)?.unread)
        chat.contact.unreadFlag = 0
        assertEquals(false, access.describe(chat)?.unread)
    }
}
