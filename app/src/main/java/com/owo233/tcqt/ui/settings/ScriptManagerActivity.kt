package com.owo233.tcqt.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.ThemeSettings
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.script.ScriptGateway
import com.owo233.tcqt.core.script.ScriptMeta
import com.owo233.tcqt.ui.component.AlertDialog
import com.owo233.tcqt.ui.component.MaterialTheme
import com.owo233.tcqt.ui.component.TextButton
import com.owo233.tcqt.ui.theme.SettingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh

/**
 * 脚本管理页：列出脚本目录下的脚本，并管理运行、自动装载、导入与删除。
 *
 * 只通过 [ScriptGateway] 访问脚本引擎，界面层不依赖 `features.script` 的实现。
 */
class ScriptManagerActivity : BaseComposeActivity() {

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
                ScriptManagerScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun ScriptManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gateway = ScriptGateway.instance

    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var scripts by remember { mutableStateOf(emptyList<ScriptMeta>()) }
    var pendingDelete by remember { mutableStateOf<ScriptMeta?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val reload: () -> Unit = { refreshToken++ }

    val importZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || gateway == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) { gateway.importZip(context.contentResolver, uri) }
            if (error == null) Toasts.success("脚本已导入") else Toasts.error(error)
            reload()
        }
    }

    LaunchedEffect(refreshToken, gateway) {
        scripts = withContext(Dispatchers.IO) { gateway?.list().orEmpty() }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除脚本") },
            text = { Text("确认删除「${target.name}」及其全部文件吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (gateway != null) {
                            withContext(Dispatchers.IO) { gateway.delete(target.id) }
                        }
                        pendingDelete = null
                        reload()
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog && gateway != null) {
        CreateScriptDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { id, name, version, author ->
                scope.launch {
                    val created = withContext(Dispatchers.IO) {
                        gateway.create(id, name, version, author)
                    }
                    showCreateDialog = false
                    if (created) Toasts.success("已创建脚本") else Toasts.error("创建失败：id 重复或目录已存在")
                    reload()
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
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "脚本引擎",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = reload) {
                    Icon(MiuixIcons.Refresh, contentDescription = "刷新")
                }
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
            item {
                ScriptToolbar(
                    enabled = gateway != null,
                    onCreate = { showCreateDialog = true },
                    onImport = {
                        importZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                )
            }

            if (scripts.isEmpty()) {
                item { ScriptEmptyCard() }
            }

            items(scripts, key = { it.id }) { meta ->
                ScriptCard(
                    meta = meta,
                    onToggleRun = {
                        if (gateway == null) return@ScriptCard
                        scope.launch {
                            val started = withContext(Dispatchers.IO) { gateway.start(meta.id) }
                            if (started) Toasts.success("${meta.name} 已启动")
                            else Toasts.info("${meta.name} 已停止")
                            reload()
                        }
                    },
                    onStop = {
                        if (gateway == null) return@ScriptCard
                        scope.launch {
                            withContext(Dispatchers.IO) { gateway.stop(meta.id) }
                            Toasts.info("${meta.name} 已停止")
                            reload()
                        }
                    },
                    onToggleAuto = {
                        if (gateway == null) return@ScriptCard
                        scope.launch {
                            withContext(Dispatchers.IO) { gateway.setAutoLoad(meta.id, !meta.autoLoad) }
                            reload()
                        }
                    },
                    onReload = {
                        if (gateway == null) return@ScriptCard
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { gateway.reload(meta.id) }
                            if (ok) Toasts.success("${meta.name} 已重载") else Toasts.error("重载失败")
                            reload()
                        }
                    },
                    onDelete = { pendingDelete = meta },
                )
            }

            item { ScriptHintCard(gateway?.scriptDir.orEmpty()) }
        }
    }
}

@Composable
private fun ScriptToolbar(enabled: Boolean, onCreate: () -> Unit, onImport: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCreate, enabled = enabled) { Text("新建脚本") }
            TextButton(onClick = onImport, enabled = enabled) { Text("导入脚本包") }
        }
    }
}

@Composable
private fun ScriptCard(
    meta: ScriptMeta,
    onToggleRun: () -> Unit,
    onStop: () -> Unit,
    onToggleAuto: () -> Unit,
    onReload: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = meta.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${meta.id} · v${meta.version} · ${meta.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (meta.running) "运行中" else "已停止",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (meta.running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(onClick = if (meta.running) onStop else onToggleRun) {
                    Text(if (meta.running) "停止" else "启动")
                }
            }

            if (meta.desc.isNotBlank()) {
                Text(
                    text = meta.desc.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "随宿主启动",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = meta.autoLoad,
                    onCheckedChange = { onToggleAuto() },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onReload) { Text("重载") }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun ScriptEmptyCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "还没有脚本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "新建一个脚本，或导入 .zip 脚本包；脚本入口文件为 main.java。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScriptHintCard(scriptDir: String) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "脚本目录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = scriptDir,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse("file://$scriptDir"), "resource/folder")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure { Toasts.error("没有可用的文件管理器") }
            }) { Text("打开目录") }
        }
    }
}

@Composable
private fun CreateScriptDialog(
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, version: String, author: String) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = id,
                    onValueChange = { id = it },
                    label = "脚本 ID",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "脚本名称",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = version,
                    onValueChange = { version = it },
                    label = "版本号",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                TextField(
                    value = author,
                    onValueChange = { author = it },
                    label = "作者",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank() && name.isNotBlank(),
                onClick = { onConfirm(id.trim(), name.trim(), version.trim(), author.trim()) },
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("取消") }
        },
    )
}
