package com.owo233.tcqt.features.appearance.chatgroups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatGroupSessionTest {
    @Test
    fun deletingSelectedGroupReturnsToAllAndRefreshesTheHost() {
        var stored = ""
        var refreshes = 0
        val session = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) { refreshes++ }
        session.syncAccount()
        val group = CustomChatGroup("custom:one", "工作")
        session.save(listOf(group), "a")
        session.select(group.id)
        session.save(emptyList(), "a")
        assertEquals("all", session.selected)
        assertEquals(3, refreshes)
        assertEquals(emptyList(), ChatGroupConfig.decode(stored).accounts["a"])
        assertEquals("all", ChatGroupConfig.decode(stored).selections["a"])
        val reopened = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) {}
        reopened.syncAccount()
        assertEquals("all", reopened.selected)
    }

    @Test
    fun accountSwitchClearsOldSourceAndRejectsAnOpenEditorFromThePreviousAccount() {
        var account = "a"
        var stored = ""
        val session = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { account }) {}
        session.syncAccount()
        session.save(listOf(CustomChatGroup("custom:a", "工作")), "a")
        session.source = listOf("old host item")
        session.chats = listOf(ChatSummary("2:20", "工作群", 2, true))
        session.select("custom:a")
        account = "b"
        assertFailsWith<IllegalStateException> { session.save(emptyList(), "a") }
        session.syncAccount()
        assertEquals("all", session.selected)
        assertTrue(session.groups.isEmpty())
        assertTrue(session.source.isEmpty())
        assertTrue(session.chats.isEmpty())
        session.save(listOf(CustomChatGroup("custom:b", "学习")), "b")
        assertEquals(setOf("a", "b"), ChatGroupConfig.decode(stored).accounts.keys)
    }

    @Test
    fun sortingMixedTabsPersistsWithoutChangingSelectionOrSubmittingConversations() {
        var stored = ""
        var refreshes = 0
        val session = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) { refreshes++ }
        session.syncAccount()
        val custom = CustomChatGroup("custom:one", "工作")
        session.save(listOf(custom), "a")
        session.select("friend")
        val source = listOf("original conversation")
        session.source = source
        val order = listOf("other", custom.id, "group", "all", "friend", "unread", "channel", "temporary")
        session.saveOrder(order, "a")
        assertEquals(order, session.tabs.map { it.id })
        assertEquals("friend", session.selected)
        assertEquals(source, session.source)
        assertEquals(2, refreshes)
        val reopened = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) {}
        reopened.syncAccount()
        assertEquals(order, reopened.tabs.map { it.id })
        assertEquals(listOf(custom), reopened.groups)
        assertEquals("friend", reopened.selected)
    }

    @Test
    fun hiddenCategoriesRetainTheirPositionAndAccountOrdersStaySeparate() {
        var account = "a"
        var stored = ""
        val categories = listOf(ChatCategory.ALL, ChatCategory.GROUP, ChatCategory.UNREAD)
        val session = ChatGroupSession(categories, { stored }, { stored = it }, { account }) {}
        session.syncAccount()
        session.saveOrder(listOf("unread", "all", "group"), "a")
        val fullOrder = listOf("unread", "all", "friend", "group", "channel", "other", "temporary")
        assertEquals(fullOrder, ChatGroupConfig.decode(stored).orders["a"])
        val allVisible = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) {}
        allVisible.syncAccount()
        assertEquals(fullOrder, allVisible.tabs.map { it.id })
        account = "b"
        assertFailsWith<IllegalStateException> { session.saveOrder(listOf("group", "all", "unread"), "a") }
        session.syncAccount()
        assertEquals(categories.map { it.id }, session.tabs.map { it.id })
        session.saveOrder(listOf("group", "unread", "all"), "b")
        assertEquals(fullOrder, ChatGroupConfig.decode(stored).orders["a"])
    }

    @Test
    fun reopeningRestoresEachBuiltinBeforeTheFirstMessageListIsFiltered() {
        var stored = ""
        val chats = listOf(
            ChatSummary("1:10", "好友", 1, false),
            ChatSummary("2:20", "群聊", 2, true),
        )
        for (category in ChatCategory.entries) {
            val session = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) {}
            session.syncAccount()
            session.select(category.id)
            val reopened = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { "a" }) {}
            reopened.syncAccount()
            assertEquals(category.id, reopened.selected)
            if (category == ChatCategory.FRIEND) {
                assertEquals(listOf(chats.first()), projectChatItems(chats, reopened.selected,
                    reopened.groups, { it }, { null }))
            }
        }
    }

    @Test
    fun customSelectionSurvivesRenameAndAccountsRestoreTheirOwnSelection() {
        var account = "a"
        var stored = ""
        val session = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { account }) {}
        session.syncAccount()
        val custom = CustomChatGroup("custom:one", "工作")
        session.save(listOf(custom), "a")
        session.select(custom.id)
        session.save(listOf(custom.copy(name = "同事")), "a")
        account = "b"
        session.syncAccount()
        assertEquals("all", session.selected)
        session.select("unread")
        account = "a"
        session.syncAccount()
        assertEquals(custom.id, session.selected)
        assertEquals("同事", session.groups.single().name)
        val reopened = ChatGroupSession(ChatCategory.entries, { stored }, { stored = it }, { account }) {}
        reopened.syncAccount()
        assertEquals(custom.id, reopened.selected)
        account = "b"
        reopened.syncAccount()
        assertEquals("unread", reopened.selected)
    }

    @Test
    fun oldConfigAndMissingOrHiddenSelectionsOpenAll() {
        val cases = listOf(
            """{"accounts":{},"orders":{}}""",
            ChatGroupConfig(selections = mapOf("a" to "custom:deleted")).encode(),
            ChatGroupConfig(selections = mapOf("a" to "friend")).encode(),
        )
        for (stored in cases) {
            val session = ChatGroupSession(listOf(ChatCategory.ALL, ChatCategory.GROUP),
                { stored }, {}, { "a" }) {}
            session.syncAccount()
            assertEquals("all", session.selected)
        }
    }
}
