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

    @Test
    fun unreadFolderExpandsGroupAssistantAndDeduplicatesItsGroups() {
        val helper = recent(7, 7, 1)
        val group = recent(20, 2, 100)
        val flagged = recent(21, 2, 0).apply { contact.unreadFlag = 1 }
        val targets = access.readTargets(listOf(helper, group, recent(10, 1, 2)), "unread", emptyList(),
            listOf(group.contact, flagged.contact, recent(22, 2, 0).contact))
        assertEquals(listOf(20L, 21L, 10L), targets.map { it.contactId })
        assertEquals(listOf(2, 2, 1), targets.map { it.chatType })
    }

    @Test
    fun customFolderExpandsOnlyWhenItContainsGroupAssistant() {
        val items = listOf(recent(7, 7, 1), recent(10, 1, 1), recent(30, 2, 1))
        val helperGroups = listOf(recent(20, 2, 100).contact)
        val folders = listOf(
            CustomChatGroup("custom:helper", "收集", mapOf("7:7" to "群助手", "1:10" to "好友")),
            CustomChatGroup("custom:friend", "好友", mapOf("1:10" to "好友")),
        )
        assertEquals(listOf(20L, 10L), access.readTargets(items, "custom:helper", folders, helperGroups)
            .map { it.contactId })
        assertEquals(listOf(10L), access.readTargets(items, "custom:friend", folders, helperGroups)
            .map { it.contactId })
    }

    @Test
    fun emptyOrReadGroupAssistantNeverBecomesAnOrdinaryReadTarget() {
        val helper = recent(7, 7, 1)
        assertEquals(listOf(7), access.readTargets(listOf(helper), "all", emptyList()).map { it.chatType })
        assertEquals(emptyList(), access.readTargets(listOf(helper), "all", emptyList(), emptyList()))
        helper.contact.unreadCnt = 0
        assertEquals(emptyList(), access.readTargets(listOf(helper), "all", emptyList(),
            listOf(recent(20, 2, 100).contact)))
    }
}
