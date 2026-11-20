package com.owo233.tcqt.features.appearance.chatgroups

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal enum class ChatCategory(val id: String, val title: String) {
    ALL("all", "所有"), GROUP("group", "群聊"), FRIEND("friend", "好友"),
    UNREAD("unread", "未读"), CHANNEL("channel", "频道"), OTHER("other", "其他"),
    TEMPORARY("temporary", "临时");
}

internal data class ChatGroupTab(val id: String, val title: String, val unreadCount: Int = 0)

internal fun chatGroupTabs(
    categories: List<ChatCategory>,
    groups: List<CustomChatGroup>,
    order: List<String>,
    chats: List<ChatSummary> = emptyList(),
): List<ChatGroupTab> {
    val positions = order.withIndex().associate { it.value to it.index }
    val tabs = categories.map { ChatGroupTab(it.id, it.title) } + groups.map { ChatGroupTab(it.id, it.name) }
    return tabs.sortedBy { positions[it.id] ?: Int.MAX_VALUE }.map { tab ->
        tab.copy(unreadCount = chats.count { it.unread && matchesChatGroup(tab.id, it, groups) })
    }
}

internal data class ChatSummary(
    val key: String,
    val title: String,
    val type: Int,
    val unread: Boolean,
    val detail: String = "",
)

@Serializable
internal data class CustomChatGroup(
    val id: String,
    val name: String,
    // Keep names for members that have temporarily left the recent list.
    val members: Map<String, String> = emptyMap(),
)

@Serializable
internal data class ChatGroupConfig(
    val accounts: Map<String, List<CustomChatGroup>> = emptyMap(),
    val orders: Map<String, List<String>> = emptyMap(),
    val selections: Map<String, String> = emptyMap(),
) {
    fun withGroups(account: String, groups: List<CustomChatGroup>) =
        copy(accounts = accounts + (account to groups))

    fun withOrder(account: String, order: List<String>) =
        copy(orders = orders + (account to order))

    fun withSelection(account: String, selected: String) =
        copy(selections = selections + (account to selected))

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String): ChatGroupConfig {
            if (value.isBlank()) return ChatGroupConfig()
            val config = json.decodeFromString<ChatGroupConfig>(value)
            // Config backups are an external boundary. Do not accept duplicate tab identities.
            config.accounts.values.forEach { groups ->
                require(groups.map { it.id }.distinct().size == groups.size)
                require(groups.all { it.id.startsWith("custom:") && it.name.isNotBlank() })
            }
            config.orders.values.forEach { order ->
                require(order.distinct().size == order.size && order.none { it.isBlank() })
            }
            return config
        }
    }
}

internal fun matchesChatGroup(
    selected: String,
    chat: ChatSummary,
    groups: List<CustomChatGroup>,
): Boolean = when (selected) {
    ChatCategory.ALL.id -> true
    ChatCategory.GROUP.id -> chat.type == 2
    ChatCategory.FRIEND.id -> chat.type == 1
    ChatCategory.UNREAD.id -> chat.unread
    // 16 is a guild conversation; 60 is QQ's guild-message folder.
    ChatCategory.CHANNEL.id -> chat.type == 16 || chat.type == 60
    // QQ's GroupMemberTempAIOInterceptor accepts chatType 100 (legacy uinType 1000).
    ChatCategory.TEMPORARY.id -> chat.type == 100
    ChatCategory.OTHER.id -> !chat.unread && when (chat.type) {
        1, 2, 16, 60, 100 -> false
        else -> true
    }
    else -> groups.firstOrNull { it.id == selected }?.members?.containsKey(chat.key) == true
}

/** Preserve host objects and order; only a filtered view unfolds QQ's folded rows. */
internal fun <T : Any> projectChatItems(
    items: List<T>,
    selected: String,
    groups: List<CustomChatGroup>,
    describe: (T) -> ChatSummary?,
    foldedItems: (T) -> List<T>?,
): List<T> {
    if (selected == ChatCategory.ALL.id) return items
    val result = ArrayList<T>(items.size)
    val seen = HashSet<String>()
    fun append(rows: List<T>) {
        for (item in rows) {
            val children = foldedItems(item)
            if (children != null) {
                append(children)
            } else {
                val chat = describe(item)
                if (chat == null || (matchesChatGroup(selected, chat, groups) && seen.add(chat.key))) {
                    result += item
                }
            }
        }
    }
    append(items)
    return result
}
