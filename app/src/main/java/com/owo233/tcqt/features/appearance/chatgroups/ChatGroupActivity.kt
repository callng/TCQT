package com.owo233.tcqt.features.appearance.chatgroups

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.CompatibleComposeActivity
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.TCQTAlertDialog
import com.owo233.tcqt.ui.component.horizontalSafePadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.UUID
import java.util.WeakHashMap

/** A parasitic host Activity; the route registry does not retain the host list or its Activity. */
class ChatGroupActivity : CompatibleComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent.getStringExtra("session")
        val session = sessions.entries.firstOrNull { it.value == route }?.key
        if (session == null || session.editingAccount() != intent.getStringExtra("account")) {
            finish()
            return
        }
        val account = session.editingAccount()
        setHostContent {
            ChatGroupManager(groups = session.groups, chats = session.chats, tabs = session.tabs,
                initialGroupId = intent.getStringExtra("group"),
                onSave = { session.save(it, account) }, onClose = ::finish)
        }
    }

    internal companion object {
        private val sessions = WeakHashMap<ChatGroupSession, String>()

        fun open(context: Context, session: ChatGroupSession, groupId: String?) {
            val route = sessions.getOrPut(session) { UUID.randomUUID().toString() }
            context.startActivity(Intent().setClassName(context.packageName, ChatGroupActivity::class.java.name)
                .putExtra("session", route).putExtra("account", session.editingAccount())
                .putExtra("group", groupId))
        }
    }
}

@Composable
internal fun ChatGroupManager(
    groups: List<CustomChatGroup>,
    chats: List<ChatSummary>,
    onSave: (List<CustomChatGroup>) -> Unit,
    onClose: () -> Unit,
    tabs: List<ChatGroupTab> = chatGroupTabs(ChatCategory.entries, groups, emptyList()),
    initialGroupId: String? = null,
    initialPicker: Boolean = false,
) {
    val initial = groups.firstOrNull { it.id == initialGroupId }
    val direct = initial != null
    var editingId by rememberSaveable { mutableStateOf(initial?.id) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var members by rememberSaveable { mutableStateOf(HashMap(initial?.members.orEmpty())) }
    var picking by rememberSaveable { mutableStateOf(initialPicker && initial != null) }
    var pickedMembers by rememberSaveable { mutableStateOf(HashMap(initial?.members.orEmpty())) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val original = groups.firstOrNull { it.id == editingId }
    val back = {
        when {
            picking -> picking = false
            editingId != null && !direct -> { editingId = null; error = null }
            else -> onClose()
        }
    }
    BackHandler(onBack = back)
    val commit: (List<CustomChatGroup>) -> Unit = { updated ->
        try {
            onSave(updated)
            editingId = null
            error = null
            deleting = false
            if (direct) onClose()
        } catch (failure: Exception) {
            error = failure.message ?: "保存失败，请重试"
        }
    }
    if (deleting) {
        TCQTAlertDialog(title = "删除文件夹“${original?.name}”？", onDismissRequest = { deleting = false },
            text = {
                Text("会话仍保留在“全部”中。")
                error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            },
            dismissButton = { ActionButton(onClick = { deleting = false }) { Text("取消") } },
            confirmButton = { ActionButton(onClick = { commit(groups.filterNot { it.id == editingId }) }) {
                Text("删除", color = MiuixTheme.colorScheme.error)
            } })
    }
    val candidates = remember(chats, members) {
        chats + members.filterKeys { key -> chats.none { it.key == key } }
            .map { (key, title) -> ChatSummary(key, title, -1, false) }
    }
    val visible = remember(candidates, query) {
        val text = query.trim()
        candidates.filter { it.title.contains(text, ignoreCase = true) || it.detail.contains(text, ignoreCase = true) }
    }
    val title = when {
        picking -> "添加聊天"
        editingId == null -> "聊天文件夹"
        original == null -> "新建文件夹"
        else -> "编辑文件夹"
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        SmallTopAppBar(title = title, navigationIcon = {
            IconButton(onClick = back) { Icon(MiuixIcons.Back, contentDescription = "返回") }
        })
    }, bottomBar = {
        if (editingId != null) AdaptiveContent { side ->
            Row(Modifier.fillMaxWidth().horizontalSafePadding().navigationBarsPadding()
                .background(MiuixTheme.colorScheme.surface)
                .padding(horizontal = side, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(primary = true, modifier = Modifier.weight(1f), onClick = {
                    if (picking) {
                        members = HashMap(pickedMembers)
                        picking = false
                    } else {
                        val trimmed = name.trim()
                        error = when {
                            trimmed.isEmpty() -> "请输入文件夹名称"
                            trimmed.length > 20 -> "文件夹名称最多 20 个字符"
                            ChatCategory.entries.any { it.title == trimmed } ||
                                groups.any { it.id != editingId && it.name == trimmed } -> "文件夹名称已存在"
                            else -> null
                        }
                        if (error == null) {
                            val group = CustomChatGroup(editingId!!, trimmed, members)
                            commit(if (original == null) groups + group else groups.map { if (it.id == group.id) group else it })
                        }
                    }
                }) { Text(if (picking) "完成 (${pickedMembers.size})" else "保存") }
            }
        }
    }) { insets ->
        AdaptiveContent { side ->
            Column(Modifier.fillMaxSize().horizontalSafePadding().padding(
                top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())) {
                if (picking) TextField(value = query, onValueChange = { query = it }, label = "搜索会话",
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = side, vertical = 12.dp))
                LazyColumn(Modifier.fillMaxSize().overScrollVertical(),
                    contentPadding = PaddingValues(start = side, end = side, top = 12.dp,
                        bottom = 24.dp)) {
                    when {
                        picking -> {
                            if (visible.isEmpty()) item { Text("没有匹配的会话", Modifier.padding(16.dp),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary) }
                            itemsIndexed(visible, key = { _, chat -> "chat:${chat.key}" }) { index, chat ->
                                PreferenceSegment(first = index == 0, last = index == visible.lastIndex) {
                                    CheckboxPreference(title = chat.title, summary = chat.detail.takeIf { it.isNotEmpty() },
                                        checked = chat.key in pickedMembers, onCheckedChange = { checked ->
                                            pickedMembers = HashMap(pickedMembers).apply {
                                                if (checked) put(chat.key, chat.title) else remove(chat.key)
                                            }
                                        })
                                }
                            }
                        }
                        editingId != null -> {
                            item(key = "name") {
                                SmallTitle("文件夹名称")
                                TextField(value = name, onValueChange = { name = it; error = null },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                error?.let { Text(it, Modifier.padding(12.dp), color = MiuixTheme.colorScheme.error) }
                            }
                            item(key = "add") {
                                SmallTitle("包含的聊天")
                                PreferenceSegment(first = true, last = members.isEmpty()) {
                                    ArrowPreference(title = "添加聊天", onClick = {
                                        pickedMembers = HashMap(members); query = ""; picking = true
                                    })
                                }
                            }
                            itemsIndexed(members.entries.toList(), key = { _, member -> member.key }) { index, member ->
                                PreferenceSegment(first = false, last = index == members.size - 1) {
                                    BasicComponent(title = member.value, endActions = {
                                        IconButton(onClick = { members = HashMap(members).apply { remove(member.key) } }) {
                                            Icon(MiuixIcons.Delete, contentDescription = "移除聊天",
                                                tint = MiuixTheme.colorScheme.onSurfaceSecondary)
                                        }
                                    })
                                }
                            }
                            if (original != null) item(key = "delete") {
                                PreferenceSegment(first = true, last = true, modifier = Modifier.padding(top = 24.dp)) {
                                    BasicComponent(title = "删除文件夹", titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
                                        onClick = { deleting = true })
                                }
                            }
                        }
                        else -> {
                            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                                val group = groups.firstOrNull { it.id == tab.id }
                                PreferenceSegment(first = index == 0, last = false) {
                                    if (group == null) BasicComponent(title = tab.title)
                                    else ArrowPreference(title = group.name, summary = "${group.members.size} 个聊天", onClick = {
                                        editingId = group.id; name = group.name; members = HashMap(group.members); error = null
                                    })
                                }
                            }
                            item(key = "create") {
                                PreferenceSegment(first = tabs.isEmpty(), last = true) {
                                    BasicComponent(title = "新建文件夹", titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.primary), onClick = {
                                        editingId = "custom:${UUID.randomUUID()}"; name = ""; members = hashMapOf(); error = null
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
