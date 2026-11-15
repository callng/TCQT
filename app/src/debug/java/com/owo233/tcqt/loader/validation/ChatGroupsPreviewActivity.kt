package com.owo233.tcqt.loader.validation

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.features.appearance.ChatGroups
import com.owo233.tcqt.features.appearance.chatgroups.ChatCategory
import com.owo233.tcqt.features.appearance.chatgroups.ChatGroupConfig
import com.owo233.tcqt.features.appearance.chatgroups.ChatGroupManager
import com.owo233.tcqt.features.appearance.chatgroups.ChatGroupTabs
import com.owo233.tcqt.features.appearance.chatgroups.ChatSummary
import com.owo233.tcqt.features.appearance.chatgroups.CustomChatGroup
import com.owo233.tcqt.features.appearance.chatgroups.chatGroupTabs
import com.owo233.tcqt.features.appearance.chatgroups.projectChatItems
import com.owo233.tcqt.ui.component.HostDialog
import com.owo233.tcqt.ui.component.CompatibleComposeView
import com.owo233.tcqt.ui.theme.SettingTheme
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.concurrent.thread

/** Standalone synthetic UI fixture. No QQ account, settings or message database is opened. */
class ChatGroupsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("scan", false)) thread(name = "chat-groups-dexkit") {
            val report = JSONObject()
            try {
                System.loadLibrary("dexkit")
                DexKitBridge.create(filesDir.resolve("qq.apk").absolutePath).use { bridge ->
                    ChatGroups.getQueryMap().forEach { (key, query) ->
                        val matches = when (query) {
                            is FindClass -> bridge.findClass(query).map { it.descriptor }
                            is FindMethod -> bridge.findMethod(query).map { it.descriptor }
                            else -> error("Unsupported query")
                        }
                        check(matches.size == 1) { "$key: $matches" }
                        report.put(key, matches.single())
                    }
                }
                report.put("passed", true)
            } catch (failure: Throwable) {
                report.put("passed", false).put("error", failure.stackTraceToString())
            }
            filesDir.resolve("chat-groups-dexkit.json").writeText(report.toString(2))
        }
        if (intent.getBooleanExtra("embedded", false)) {
            showEmbeddedHeader()
            return
        }
        val preferences = getPreferences(MODE_PRIVATE)
        val initial = ChatGroupConfig.decode(preferences.getString("groups", "").orEmpty())
        val chats = listOf(
            ChatSummary("1:10", "示例好友 · 小林", 1, false),
            ChatSummary("2:20", "示例群聊 · 周末徒步", 2, true),
            ChatSummary("1:11", "示例好友 · 小陈", 1, true),
            ChatSummary("16:30", "示例频道 · 摄影交流", 16, true),
            ChatSummary("30:40", "示例通知 · 已读", 30, false),
            ChatSummary("30:41", "示例通知 · 未读", 30, true),
        )
        setContent {
            var config by remember { mutableStateOf(initial) }
            val groups = config.accounts["preview"].orEmpty()
            var selected by remember { mutableStateOf("all") }
            var manage by remember { mutableStateOf(false) }
            val rows = projectChatItems(chats, selected, groups, { it }, { null })
            SettingTheme(themeMode = ModuleThemeMode.System, monetEnabled = false) {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
                        .systemBarsPadding().padding(padding)) {
                        Text("聊天分组预览", Modifier.padding(20.dp), style = MiuixTheme.textStyles.title3)
                        TextField("", {}, label = "搜索", readOnly = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        ChatGroupTabs(chatGroupTabs(ChatCategory.entries, groups, config.orders["preview"].orEmpty()),
                            selected, rows.isEmpty(), onSelect = { selected = it }, onManage = { manage = true },
                            onReorder = {
                                config = config.withOrder("preview", it)
                                preferences.edit().putString("groups", config.encode()).apply()
                            })
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(rows, key = { it.key }) {
                                Text(it.title, Modifier.fillMaxWidth().padding(20.dp))
                            }
                        }
                    }
                    HostDialog(manage, { manage = false }, {}) {
                        ChatGroupManager(groups, chats, onSave = { updated: List<CustomChatGroup> ->
                            config = config.withGroups("preview", updated)
                            preferences.edit().putString("groups", config.encode()).apply()
                            if (ChatCategory.entries.none { it.id == selected } && groups.none { it.id == selected }) selected = "all"
                        }, onClose = { manage = false })
                    }
                }
            }
        }
    }

    private fun showEmbeddedHeader() {
        HookEnv.setModuleDataPath(filesDir.resolve("header-fixture").absolutePath)
        val selected = mutableStateOf("all")
        val order = mutableStateOf(emptyList<String>())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 100, 0, 0)
            addView(TextView(context).apply { text = "原生列表头部 · 生命周期验证" }, LinearLayout.LayoutParams(-1, 150))
        }
        val header = CompatibleComposeView(this) {
            ChatGroupTabs(chatGroupTabs(ChatCategory.entries, emptyList(), order.value), selected.value, false,
                onSelect = { selected.value = it }, onManage = {}, onReorder = { order.value = it })
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        // QQ's window does not expose the module's AndroidX owners. Reproduce that boundary.
        window.decorView.setViewTreeLifecycleOwner(null)
        window.decorView.setViewTreeSavedStateRegistryOwner(null)
        header.doOnPreDraw {
            val before = header.height
            header.post {
                root.removeView(header)
                root.addView(header, LinearLayout.LayoutParams(-1, -2))
                header.doOnPreDraw {
                    filesDir.resolve("chat-groups-header.json").writeText(JSONObject()
                        .put("before", before).put("after", header.height)
                        .put("passed", before > 0 && header.height == before).toString(2))
                }
            }
        }
    }
}
