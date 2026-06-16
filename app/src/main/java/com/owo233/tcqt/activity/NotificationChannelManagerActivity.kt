package com.owo233.tcqt.activity

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat

class NotificationChannelManagerActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingTheme(darkTheme = isDarkTheme) {
                BackHandler { finish() }
                NotificationChannelManagerScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun NotificationChannelManagerScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationManager = remember { NotificationManagerCompat.from(context) }
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    val groups = remember(refreshToken) {
        notificationManager.notificationChannelGroupsCompat
    }
    val channels = remember(refreshToken) {
        notificationManager.notificationChannelsCompat
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(target.title) },
            text = { Text(target.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        target.delete(notificationManager)
                        pendingDelete = null
                        refreshToken++
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "通知渠道管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groups.isEmpty() && channels.isEmpty()) {
                item { BoxMessage("暂无通知渠道") }
            }

            items(groups, key = { "group_${it.id}" }) { group ->
                ChannelGroupCard(
                    group = group,
                    onDelete = { pendingDelete = DeleteTarget.Group(group.id, group.name.toString()) },
                    onDeleteChannel = { channel ->
                        pendingDelete = DeleteTarget.Channel(channel.id, channel.name.toString())
                    }
                )
            }

            val groupedIds = groups.flatMap { it.channels.map { channel -> channel.id } }.toSet()
            val ungrouped = channels.filterNot { it.id in groupedIds }
            items(ungrouped, key = { "channel_${it.id}" }) { channel ->
                ChannelCard(
                    channel = channel,
                    onDelete = { pendingDelete = DeleteTarget.Channel(channel.id, channel.name.toString()) }
                )
            }
        }
    }
}

@Composable
private fun ChannelGroupCard(
    group: NotificationChannelGroupCompat,
    onDelete: () -> Unit,
    onDeleteChannel: (NotificationChannelCompat) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeletableHeader(
                title = "渠道组：${group.name} (${group.id})",
                desc = group.description,
                onDelete = onDelete
            )
            group.channels.forEachIndexed { index, channel ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ChannelRow(channel = channel, onDelete = { onDeleteChannel(channel) })
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: NotificationChannelCompat, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        ChannelRow(channel = channel, onDelete = onDelete, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ChannelRow(
    channel: NotificationChannelCompat,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    DeletableHeader(
        title = "渠道：${channel.name} (${channel.id})",
        desc = buildString {
            channel.parentChannelId?.let { append("父渠道：").append(it) }
            channel.description?.let {
                if (isNotEmpty()) append('\n')
                append(it)
            }
        }.ifBlank { null },
        onDelete = onDelete,
        modifier = modifier
    )
}

@Composable
private fun DeletableHeader(
    title: String,
    desc: String?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BoxMessage(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp)
        )
    }
}

private sealed class DeleteTarget(
    val title: String,
    val message: String
) {
    abstract fun delete(notificationManager: NotificationManagerCompat)

    class Group(private val id: String, name: String) : DeleteTarget(
        title = "删除渠道组",
        message = "确认删除渠道组 $name($id) 及其所有子渠道吗？"
    ) {
        override fun delete(notificationManager: NotificationManagerCompat) {
            notificationManager.deleteNotificationChannelGroup(id)
        }
    }

    class Channel(private val id: String, name: String) : DeleteTarget(
        title = "删除渠道",
        message = "确认删除渠道 $name($id) 吗？"
    ) {
        override fun delete(notificationManager: NotificationManagerCompat) {
            notificationManager.deleteNotificationChannel(id)
        }
    }
}
