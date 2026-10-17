package com.owo233.tcqt.ui.settings

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.owo233.tcqt.core.config.ThemeSettings
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.TCQTAlertDialog
import com.owo233.tcqt.ui.component.horizontalSafePadding
import com.owo233.tcqt.ui.theme.SettingTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class NotificationChannelManagerActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode = ThemeSettings.themeMode
            val monetEnabled = ThemeSettings.monetEnabled
            SideEffect {
                updateStatusBarAppearance(themeMode.resolveDark(isDarkTheme))
            }
            SettingTheme(
                themeMode = themeMode,
                monetEnabled = monetEnabled,
                systemDarkTheme = isDarkTheme,
            ) {
                BackHandler { finish() }
                NotificationChannelManagerScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun NotificationChannelManagerScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { NotificationManagerCompat.from(context) }
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    val groups = remember(refreshToken) { manager.notificationChannelGroupsCompat }
    val channels = remember(refreshToken) { manager.notificationChannelsCompat }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    pendingDelete?.let { target ->
        TCQTAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = target.title,
            text = { Text(target.message) },
            confirmButton = {
                ActionButton(
                    onClick = {
                        pendingDelete = null
                        runCatching { target.delete(manager) }
                            .onFailure {
                                scope.launch {
                                    snackbar.showSnackbar("删除失败：${it.message ?: "请稍后重试"}")
                                }
                            }
                        refreshToken++
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { ActionButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "通知渠道管理",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) },
    ) { insets ->
        AdaptiveContent { sidePadding ->
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .horizontalSafePadding()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .overScrollVertical(),
                contentPadding =
                    PaddingValues(
                        start = sidePadding,
                        end = sidePadding,
                        top = insets.calculateTopPadding() + 12.dp,
                        bottom = insets.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                if (groups.isEmpty() && channels.isEmpty()) {
                    item { EmptyStateCard(MiuixIcons.Delete, "暂无通知渠道") }
                }
                groups.forEach { group ->
                    item(key = "group_${group.id}") {
                        SmallTitle(text = group.name.toString())
                        PreferenceSegment(first = true, last = group.channels.isEmpty()) {
                            BasicComponent(
                                title = "渠道组：${group.name}",
                                summary = group.description,
                                endActions = {
                                    DeleteChannelButton {
                                        pendingDelete =
                                            DeleteTarget.Group(group.id, group.name.toString())
                                    }
                                },
                            )
                        }
                    }
                    itemsIndexed(group.channels, key = { _, channel -> "channel_${channel.id}" }) {
                        index,
                        channel ->
                        PreferenceSegment(first = false, last = index == group.channels.lastIndex) {
                            ChannelRow(channel) {
                                pendingDelete =
                                    DeleteTarget.Channel(channel.id, channel.name.toString())
                            }
                        }
                    }
                }
                val groupedIds =
                    groups.flatMap { it.channels.map { channel -> channel.id } }.toSet()
                val ungrouped = channels.filterNot { it.id in groupedIds }
                if (ungrouped.isNotEmpty()) item(key = "ungrouped") { SmallTitle(text = "其他渠道") }
                itemsIndexed(ungrouped, key = { _, channel -> "channel_${channel.id}" }) {
                    index,
                    channel ->
                    PreferenceSegment(first = index == 0, last = index == ungrouped.lastIndex) {
                        ChannelRow(channel) {
                            pendingDelete =
                                DeleteTarget.Channel(channel.id, channel.name.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: NotificationChannelCompat, onDelete: () -> Unit) {
    val isDefault = channel.id == android.app.NotificationChannel.DEFAULT_CHANNEL_ID
    BasicComponent(
        title = channel.name.toString(),
        summary =
            buildString {
                append(channel.id)
                channel.parentChannelId?.let { append(" · 父渠道：").append(it) }
                channel.description?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
                if (isDefault) append("\n系统默认渠道")
            },
        endActions = { if (!isDefault) DeleteChannelButton(onDelete) },
    )
}

@Composable
private fun DeleteChannelButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(MiuixIcons.Delete, contentDescription = "删除", tint = MiuixTheme.colorScheme.error)
    }
}

private sealed class DeleteTarget(
    val title: String,
    val message: String,
) {
    abstract fun delete(notificationManager: NotificationManagerCompat)

    class Group(private val id: String, name: String) :
        DeleteTarget(
            title = "删除渠道组",
            message = "确认删除渠道组 $name($id) 及其所有子渠道吗？",
        ) {
        override fun delete(notificationManager: NotificationManagerCompat) {
            notificationManager.deleteNotificationChannelGroup(id)
        }
    }

    class Channel(private val id: String, name: String) :
        DeleteTarget(
            title = "删除渠道",
            message = "确认删除渠道 $name($id) 吗？",
        ) {
        override fun delete(notificationManager: NotificationManagerCompat) {
            notificationManager.deleteNotificationChannel(id)
        }
    }
}
