package com.owo233.tcqt.features.appearance.chatgroups

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.HostDialog
import com.owo233.tcqt.ui.component.PreferenceSegment
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import java.util.UUID
import kotlinx.coroutines.launch

internal class ChatGroupDialog(
    context: Context,
    private val session: ChatGroupSession,
    private val chats: List<ChatSummary>,
) : CompatibleComposeDialog(context) {
    private val account = session.editingAccount()

    @Composable
    override fun DialogContent() {
        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            ChatGroupManager(
                groups = session.groups,
                chats = chats,
                onSave = { session.save(it, account) },
                onClose = ::dismissWithAnimation,
            )
        }
    }
}

@Composable
internal fun ChatGroupManager(
    groups: List<CustomChatGroup>,
    chats: List<ChatSummary>,
    onSave: (List<CustomChatGroup>) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<CustomChatGroup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val commit: (List<CustomChatGroup>) -> Unit = { updated ->
        try {
            onSave(updated)
            editing = null
            error = null
        } catch (failure: Exception) {
            error = failure.message ?: "保存失败，请重试"
        }
    }
    val editTarget = editing
    when {
        editTarget != null -> key(editTarget.id) {
            ChatGroupEditor(
                group = editTarget,
                groups = groups,
                chats = chats,
                saveError = error,
                onCancel = { editing = null; error = null },
                onDelete = if (groups.any { it.id == editTarget.id })
                    ({ commit(groups.filterNot { it.id == editTarget.id }) }) else null,
                onSave = { group ->
                    commit(if (groups.any { it.id == group.id }) groups.map { if (it.id == group.id) group else it }
                        else groups + group)
                },
            )
        }
        else -> Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("管理分组", style = MiuixTheme.textStyles.title4)
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (groups.isEmpty()) item {
                    Text("暂无自定义分组", Modifier.padding(vertical = 24.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
                itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
                    PreferenceSegment(first = index == 0, last = index == groups.lastIndex) {
                        ArrowPreference(
                            title = group.name,
                            summary = "${group.members.size} 个会话",
                            onClick = { editing = group },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("关闭") }
                ActionButton(
                    onClick = { editing = CustomChatGroup("custom:${UUID.randomUUID()}", "") },
                    primary = true,
                    modifier = Modifier.weight(1f),
                ) { Text("新建分组") }
            }
        }
    }
}

@Composable
private fun ChatGroupEditor(
    group: CustomChatGroup,
    groups: List<CustomChatGroup>,
    chats: List<ChatSummary>,
    saveError: String?,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (CustomChatGroup) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var query by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(group.members) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val candidates = remember(chats, group.members) {
        chats + group.members.filterKeys { key -> chats.none { it.key == key } }
            .map { (key, title) -> ChatSummary(key, title, -1, false) }
    }
    val visible = remember(candidates, query) {
        val text = query.trim()
        candidates.filter { it.title.contains(text, ignoreCase = true) || it.detail.contains(text, ignoreCase = true) }
    }
    if (confirmDelete) {
        DialogForm(
            title = "删除“${group.name}”？",
            actions = {
                ActionButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                ActionButton(onClick = { onDelete?.invoke() }, modifier = Modifier.weight(1f)) {
                    Text("删除", color = MiuixTheme.colorScheme.error)
                }
            },
        ) {
            Text("会话仍保留在“全部”中。")
            saveError?.let { Text(it, color = MiuixTheme.colorScheme.error) }
        }
        return
    }
    val actions: @Composable RowScope.() -> Unit = {
        onDelete?.let {
            ActionButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                Text("删除", color = MiuixTheme.colorScheme.error)
            }
        }
        ActionButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
        ActionButton(
            onClick = {
                val trimmed = name.trim()
                inputError = when {
                    trimmed.isEmpty() -> "请输入分组名称"
                    trimmed.length > 20 -> "分组名称最多 20 个字符"
                    ChatCategory.entries.any { it.title == trimmed } ||
                        groups.any { it.id != group.id && it.name == trimmed } -> "分组名称已存在"
                    else -> null
                }
                if (inputError == null) onSave(group.copy(name = trimmed, members = members))
                else scope.launch { listState.scrollToItem(1) }
            },
            primary = true,
            modifier = Modifier.weight(1f),
        ) { Text("保存") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        // A landscape IME can leave less room than the fixed actions and a single input.
        val compact = maxHeight < 200.dp
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyColumn(Modifier.weight(1f, fill = false), state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "title") {
                    Text(if (onDelete == null) "新建分组" else "编辑分组", style = MiuixTheme.textStyles.title4)
                }
                item(key = "name") {
                    TextField(
                        value = name, onValueChange = { name = it; inputError = null },
                        label = "分组名称", singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    (inputError ?: saveError)?.let {
                        Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
                    }
                }
                item(key = "search") {
                    TextField(
                        value = query, onValueChange = { query = it }, label = "搜索会话",
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "selection") {
                    Text("已选 ${members.size} 个会话", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
                if (visible.isEmpty()) item {
                    Text("没有匹配的会话", Modifier.padding(vertical = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
                items(visible, key = { "chat:${it.key}" }) { chat ->
                    val checked = chat.key in members
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(checked, role = Role.Checkbox) { selected ->
                                members = if (selected) members + (chat.key to chat.title) else members - chat.key
                            }.padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(if (checked) ToggleableState.On else ToggleableState.Off, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, style = MiuixTheme.textStyles.body1)
                            if (chat.detail.isNotEmpty()) Text(chat.detail, style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                }
                if (compact) item(key = "actions") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
                }
            }
            if (!compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        }
    }
}
