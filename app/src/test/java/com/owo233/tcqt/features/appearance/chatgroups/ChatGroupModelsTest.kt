package com.owo233.tcqt.features.appearance.chatgroups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ChatGroupModelsTest {
    private data class Row(val chat: ChatSummary? = null, val children: List<Row>? = null)
    private val header = Row()
    private val friend = Row(ChatSummary("1:10", "小明", 1, false))
    private val group = Row(ChatSummary("2:20", "工作群", 2, true))
    private val channel = Row(ChatSummary("16:30", "频道", 16, true))
    private val folder = Row(children = listOf(friend, group))
    private val custom = CustomChatGroup("custom:work", "工作", mapOf("2:20" to "工作群", "16:30" to "频道"))

    private fun project(rows: List<Row>, selected: String, groups: List<CustomChatGroup> = listOf(custom)) =
        projectChatItems(rows, selected, groups, { it.chat }, { it.children })

    @Test
    fun badgesCountUnreadConversationsInEachFolderAndFollowReadUpdates() {
        val chats = listOf(friend.chat!!, group.chat!!, channel.chat!!)
        val counts = chatGroupTabs(ChatCategory.entries, listOf(custom), emptyList(), chats)
            .associate { it.id to it.unreadCount }
        assertEquals(2, counts["all"])
        assertEquals(1, counts["group"])
        assertEquals(0, counts["friend"])
        assertEquals(2, counts["unread"])
        assertEquals(1, counts["channel"])
        assertEquals(2, counts[custom.id])
        val readGroup = chats.map { if (it.key == group.chat.key) it.copy(unread = false) else it }
        val updated = chatGroupTabs(ChatCategory.entries, listOf(custom), emptyList(), readGroup)
            .associate { it.id to it.unreadCount }
        assertEquals(1, updated["all"])
        assertEquals(0, updated["group"])
        assertEquals(1, updated[custom.id])
    }

    @Test
    fun allPreservesOriginalListAndFoldObjects() {
        val original = listOf(header, folder, channel)
        assertSame(original, project(original, "all"))
        project(original, "group")
        assertEquals(listOf(friend, group), folder.children)
        assertSame(original, project(original, "all"))
    }

    @Test
    fun filteredListUnfoldsMatchingChatsKeepsHeaderOrderAndAvoidsDuplicateIds() {
        val original = listOf(header, folder, group, channel)
        assertEquals(listOf(header, group), project(original, "group"))
        assertEquals(listOf(header, friend), project(original, "friend"))
        assertEquals(listOf(header, group, channel), project(original, custom.id))
        assertEquals(listOf(header, channel), project(original, "channel"))
    }

    @Test
    fun unreadRefreshDropsReadChatsAndIncludesNewConversationsFromFullSource() {
        val original = listOf(header, friend, group)
        assertEquals(listOf(header, group), project(original, "unread"))
        val readGroup = group.copy(chat = group.chat!!.copy(unread = false))
        val updated = listOf(header, friend, readGroup, channel)
        assertEquals(listOf(header, channel), project(updated, "unread"))
        assertSame(updated, project(updated, "all"))
    }

    @Test
    fun temporaryChatsHaveTheirOwnCategoryAndRemainAvailableInAllAndCustomGroups() {
        val temporary = Row(ChatSummary("100:10", "临时会话", 100, false))
        val unknown = Row(ChatSummary("999:10", "未知会话", 999, false))
        val original = listOf(header, friend, group, channel, temporary, unknown)
        assertEquals(listOf(header, temporary), project(original, "temporary"))
        assertEquals(listOf(header, friend), project(original, "friend"))
        assertEquals(listOf(header, group), project(original, "group"))
        assertEquals(listOf(header, channel), project(original, "channel"))
        assertEquals(listOf(header, unknown), project(original, "other"))
        val selected = CustomChatGroup("custom:temp", "临时", mapOf("100:10" to "临时会话"))
        assertEquals(listOf(header, temporary), project(original, selected.id, listOf(selected)))
        assertSame(original, project(original, "all"))
    }

    @Test
    fun temporaryChatsStayInTheirCategoryWhenReadAndCanOverlapUnread() {
        val read = Row(ChatSummary("100:10", "临时会话", 100, false))
        val unread = read.copy(chat = read.chat!!.copy(unread = true))
        val original = listOf(header, Row(children = listOf(unread)), unread)
        assertEquals(listOf(header, unread), project(original, "temporary"))
        assertEquals(listOf(header, unread), project(original, "unread"))
        assertEquals(listOf(header), project(original, "other"))
        val updated = listOf(header, read)
        assertEquals(updated, project(updated, "temporary"))
        assertEquals(listOf(header), project(updated, "unread"))
        assertEquals(listOf(header), project(updated, "other"))
    }

    @Test
    fun otherKeepsReadServiceAndUnknownChatsButExcludesUnreadFriendsGroupsAndChannels() {
        val notification = Row(ChatSummary("30:40", "通知", 30, false))
        val unknown = Row(ChatSummary("999:50", "其他会话", 999, false))
        val unreadNotification = Row(ChatSummary("30:41", "未读通知", 30, true))
        val readChannel = channel.copy(chat = channel.chat!!.copy(unread = false))
        val channelFolder = Row(ChatSummary("60:60", "频道消息", 60, false))
        val folded = Row(children = listOf(notification, friend, unknown, unreadNotification))
        val original = listOf(header, folded, notification, group, readChannel, channelFolder)
        assertEquals(listOf(header, notification, unknown), project(original, "other"))
        assertEquals(listOf(header, unreadNotification, group), project(original, "unread"))
        assertSame(original, project(original, "all"))
    }

    @Test
    fun otherAndUnreadMoveServiceChatWhenReadStateChanges() {
        val read = Row(ChatSummary("30:40", "通知", 30, false))
        val unread = read.copy(chat = read.chat!!.copy(unread = true))
        assertEquals(listOf(header, read), project(listOf(header, read), "other"))
        assertEquals(listOf(header), project(listOf(header, unread), "other"))
        assertEquals(listOf(header, unread), project(listOf(header, unread), "unread"))
        assertEquals(listOf(header, read), project(listOf(header, read), "other"))
        assertEquals(listOf(header), project(listOf(header, read), "unread"))
    }

    @Test
    fun accountConfigurationsRoundTripWithoutDroppingAbsentMembersOrOtherAccounts() {
        val config = ChatGroupConfig(mapOf("accountA" to listOf(custom), "accountB" to listOf(custom.copy(name = "学习"))))
        val renamed = custom.copy(name = "同事")
        val restored = ChatGroupConfig.decode(config.withGroups("accountA", listOf(renamed)).encode())
        assertEquals(listOf(renamed), restored.accounts["accountA"])
        assertEquals(config.accounts["accountB"], restored.accounts["accountB"])
        assertEquals(custom.members, restored.accounts.getValue("accountA").single().members)
        assertEquals(emptyList(), restored.withGroups("accountA", emptyList()).accounts["accountA"])
    }

    @Test
    fun malformedExternalConfigurationCannotCreateConflictingTabs() {
        assertFailsWith<IllegalArgumentException> {
            ChatGroupConfig.decode(ChatGroupConfig(mapOf("a" to listOf(custom, custom))).encode())
        }
        assertFailsWith<IllegalArgumentException> {
            ChatGroupConfig.decode(ChatGroupConfig(mapOf("a" to listOf(custom.copy(id = "all")))).encode())
        }
        assertEquals(ChatGroupConfig(), ChatGroupConfig.decode(""))
    }

    @Test
    fun oldConfigurationKeepsDefaultOrderAndNewGroupsAppendAfterTheSavedOrder() {
        val old = ChatGroupConfig.decode("""{"accounts":{"a":[{"id":"custom:work","name":"工作"}]}}""")
        assertEquals(ChatCategory.entries.map { it.id } + custom.id,
            chatGroupTabs(ChatCategory.entries, old.accounts.getValue("a"), old.orders["a"].orEmpty()).map { it.id })
        val order = listOf(custom.id, "unread", "all", "group", "friend", "channel", "other", "temporary")
        val added = CustomChatGroup("custom:new", "新增")
        assertEquals(order + added.id, chatGroupTabs(ChatCategory.entries,
            listOf(custom.copy(name = "已改名"), added), order).map { it.id })
        assertEquals(order.filterNot { it == custom.id },
            chatGroupTabs(ChatCategory.entries, emptyList(), order).map { it.id })
    }

    @Test
    fun orderRoundTripsAndRejectsDuplicateExternalIdentities() {
        val config = ChatGroupConfig(mapOf("a" to listOf(custom))).withOrder("a", listOf(custom.id, "all"))
        assertEquals(config, ChatGroupConfig.decode(config.encode()))
        assertFailsWith<IllegalArgumentException> {
            ChatGroupConfig.decode(config.withOrder("a", listOf("all", "all")).encode())
        }
    }

    @Test
    fun newTemporaryCategoryPreservesSavedOrderAndExistingVisibilityBits() {
        val oldOrder = listOf("all", "channel", "group", "unread", "friend", "other", custom.id)
        assertEquals(oldOrder + "temporary", chatGroupTabs(ChatCategory.entries, listOf(custom), oldOrder).map { it.id })
        val categories = ChatCategory.entries.drop(1)
        assertEquals(listOf("group", "friend", "unread", "channel", "other"),
            categories.filterIndexed { index, _ -> 31 and (1 shl index) != 0 }.map { it.id })
        assertEquals(listOf(ChatCategory.TEMPORARY),
            categories.filterIndexed { index, _ -> 32 and (1 shl index) != 0 })
    }
}
