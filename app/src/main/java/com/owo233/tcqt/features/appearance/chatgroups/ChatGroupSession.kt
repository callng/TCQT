package com.owo233.tcqt.features.appearance.chatgroups

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/** One main-list instance. Only weak references point back to the host's adapter and views. */
internal class ChatGroupSession(
    val categories: List<ChatCategory>,
    private val readConfig: () -> String,
    private val writeConfig: (String) -> Unit,
    private val currentAccount: () -> String,
    private val refresh: () -> Unit,
) {
    var selected by mutableStateOf(ChatCategory.ALL.id)
        private set
    var groups by mutableStateOf(emptyList<CustomChatGroup>())
        private set
    private var order by mutableStateOf(emptyList<String>())
    var chats by mutableStateOf(emptyList<ChatSummary>())
    val tabs: List<ChatGroupTab> get() = chatGroupTabs(categories, groups, order, chats)
    var source: List<Any> = emptyList()
    var header = WeakReference<View>(null)
    var originalHeader = WeakReference<View>(null)
    private var account by mutableStateOf("")

    fun syncAccount() {
        val current = currentAccount()
        if (current == account) return
        val config = ChatGroupConfig.decode(readConfig())
        account = current
        groups = config.accounts[current].orEmpty()
        order = config.orders[current].orEmpty()
        source = emptyList()
        chats = emptyList()
        selected = config.selections[current]?.takeIf { id -> tabs.any { it.id == id } }
            ?: ChatCategory.ALL.id
    }

    fun select(id: String) {
        if (id == selected) return
        val config = ChatGroupConfig.decode(readConfig())
        writeConfig(config.withSelection(account, id).encode())
        selected = id
        refresh()
    }

    fun save(updated: List<CustomChatGroup>, editingAccount: String) {
        check(editingAccount == currentAccount() && editingAccount == account) {
            "QQ 账号已切换，请重新打开分组管理"
        }
        val config = ChatGroupConfig.decode(readConfig())
        val selection = selected.takeIf { id ->
            categories.any { it.id == id } || updated.any { it.id == id }
        } ?: ChatCategory.ALL.id
        writeConfig(config.withGroups(account, updated).withSelection(account, selection).encode())
        groups = updated
        selected = selection
        refresh()
    }

    fun editingAccount(): String = account

    fun saveOrder(visibleOrder: List<String>, editingAccount: String) {
        check(editingAccount == currentAccount() && editingAccount == account) {
            "QQ 账号已切换，请重新排列分组"
        }
        // Hidden built-in categories keep their slots, so showing them again retains their position.
        val visible = visibleOrder.toSet()
        val reordered = visibleOrder.iterator()
        val updated = chatGroupTabs(ChatCategory.entries, groups, order).map {
            if (it.id in visible) reordered.next() else it.id
        }
        if (updated == order) return
        val config = ChatGroupConfig.decode(readConfig())
        writeConfig(config.withOrder(account, updated).encode())
        order = updated
    }
}
