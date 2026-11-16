package com.owo233.tcqt.features.appearance.chatgroups

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RecentChatAccessTest {
    // Older QQ's recent item also has a List field, unrelated to folded chats.
    private class RecentItem(private val contact: RecentContactInfo, private val decoration: List<String> = emptyList())
    private open class FoldItem(private val children: List<Any>)
    private class PinnedFold(children: List<Any>) : FoldItem(children)

    @Test
    fun flatHostWithoutFoldClassFiltersNativeContactsAndKeepsHeaders() {
        val access = RecentChatAccess(RecentItem::class.java, null)
        val header = Any()
        val friend = RecentItem(RecentContactInfo().apply {
            chatType = 1
            contactId = 10
            peerName = "好友"
        }, listOf("badge"))
        val group = RecentItem(RecentContactInfo().apply {
            chatType = 2
            contactId = 20
            peerName = "群聊"
            unreadFlag = 1
        })
        val source = listOf(header, friend, group)

        assertNull(access.foldedItems(friend))
        assertNull(access.describe(header))
        assertEquals(listOf("1:10", "2:20"), access.catalog(source).map { it.key })
        assertEquals(listOf(header, group), projectChatItems(source, "unread", emptyList(),
            access::describe, access::foldedItems))
        assertSame(source, projectChatItems(source, "all", emptyList(),
            access::describe, access::foldedItems))
    }

    @Test
    fun foldedHostReadsPrivateBaseFieldAndPreservesOriginalFoldOnAll() {
        val access = RecentChatAccess(RecentItem::class.java, FoldItem::class.java)
        val header = Any()
        val temporary = RecentItem(RecentContactInfo().apply {
            chatType = 100
            contactId = 30
            peerName = "临时会话"
        })
        val children = listOf(temporary)
        val fold = PinnedFold(children)
        val source = listOf(header, fold, temporary)

        assertSame(children, access.foldedItems(fold))
        assertNull(access.foldedItems(temporary))
        assertEquals(listOf("100:30"), access.catalog(source).map { it.key })
        assertEquals(listOf(header, temporary), projectChatItems(source, "temporary", emptyList(),
            access::describe, access::foldedItems))
        assertSame(source, projectChatItems(source, "all", emptyList(),
            access::describe, access::foldedItems))
        assertSame(children, access.foldedItems(fold))
    }
}
