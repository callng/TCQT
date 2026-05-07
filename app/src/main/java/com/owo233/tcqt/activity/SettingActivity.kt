@file:OptIn(ExperimentalFoundationApi::class)

package com.owo233.tcqt.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.func.ModuleCommand
import com.owo233.tcqt.utils.ConfigBackupManager
import com.owo233.tcqt.utils.log.Log
import kotlinx.coroutines.launch

class SettingActivity : BaseComposeActivity() {

    private var onRestoreSuccessCallback: (() -> Unit)? = null

    private val backupDirectoryPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val data = result.data ?: return@registerForActivityResult
        val uri = data.data ?: return@registerForActivityResult

        val takeFlags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        runCatching {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }.onFailure { e ->
            Log.w("Failed to persist tree uri permission: $uri, flags=$takeFlags", e)
        }

        if (!ConfigBackupManager.isBackupDirectoryUriValid(this, uri)) {
            Toasts.error("目录无效或无写入权限，请重新选择")
            return@registerForActivityResult
        }

        ConfigBackupManager.saveBackupDirectoryUri(uri)
        performBackupToDirectory(uri)
    }

    private val restoreFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: Exception) {
                }
                performRestore(uri) {
                    onRestoreSuccessCallback?.invoke()
                }
            }
        }

    private fun startBackup(forceChooseDirectory: Boolean = false) {
        if (forceChooseDirectory) {
            ConfigBackupManager.clearBackupDirectoryUri()
            launchBackupDirectoryPicker()
            return
        }

        val savedUri = ConfigBackupManager.getBackupDirectoryUri()
        if (savedUri != null && ConfigBackupManager.isBackupDirectoryUriValid(this, savedUri)) {
            performBackupToDirectory(savedUri)
        } else {
            ConfigBackupManager.clearBackupDirectoryUri()
            launchBackupDirectoryPicker()
        }
    }

    private fun launchBackupDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        backupDirectoryPicker.launch(intent)
    }

    private fun performBackupToDirectory(directoryUri: Uri) {
        val success = ConfigBackupManager.backupConfigToDirectory(this, directoryUri)
        if (success) {
            Toasts.success("备份成功")
        } else {
            ConfigBackupManager.clearBackupDirectoryUri()
            Toasts.error("备份失败，请手动重新选择目录")
        }
    }

    private fun performRestore(uri: Uri, onSuccess: () -> Unit) {
        when (val result = ConfigBackupManager.restoreConfig(this, uri)) {
            is ConfigBackupManager.RestoreResult.Success -> {
                Toasts.success("还原成功，已恢复 ${result.count} 项配置")
                onSuccess()
            }

            ConfigBackupManager.RestoreResult.InvalidFile -> {
                Toasts.error("无效的备份文件")
            }

            ConfigBackupManager.RestoreResult.VersionMismatch -> {
                Toasts.error("备份文件版本不兼容")
            }

            is ConfigBackupManager.RestoreResult.Error -> {
                Toasts.error("还原失败: ${result.exception.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingTheme(darkTheme = isDarkTheme) {
                val viewModel: SettingViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                var restartPrompt by rememberSaveable { mutableStateOf<RestartPrompt?>(null) }
                var showClearDialog by rememberSaveable { mutableStateOf(false) }
                var showBackupDialog by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    onRestoreSuccessCallback = {
                        viewModel.reloadPersistedSettings()
                        viewModel.recalculateStats()
                        restartPrompt = RestartPrompt.Restore
                    }
                }

                BackHandler {
                    when {
                        viewModel.isSearchActive -> viewModel.exitSearch()
                        !viewModel.isAtRoot -> viewModel.navigateUp()
                        else -> finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("清空配置") },
                        text = { Text("是否清空所有模块配置？该操作不可逆，清空后将恢复为默认值。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearDialog = false
                                    viewModel.clearAllSettings()
                                    restartPrompt = RestartPrompt.Clear
                                }
                            ) {
                                Text("清空")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("取消")
                            }
                        }
                    )
                }

                if (showBackupDialog) {
                    AlertDialog(
                        onDismissRequest = { showBackupDialog = false },
                        title = {
                            Text(
                                "备份/还原",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "请选择你要执行的操作",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                showBackupDialog = false
                                                startBackup()
                                            },
                                            onLongClick = {
                                                showBackupDialog = false
                                                startBackup(forceChooseDirectory = true)
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Backup,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                "备份模块设置",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "导出模块设置到外部存储文件。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                showBackupDialog = false
                                                restoreFilePicker.launch(arrayOf("application/json"))
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Restore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                "还原模块设置",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                "读取配置文件并覆盖当前设置。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                showBackupDialog = false
                                                showClearDialog = true
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                "清空模块设置",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                "清空全部配置并恢复默认行为。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showBackupDialog = false }) {
                                Text(
                                    "取消",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    )
                }

                if (restartPrompt != null) {
                    val prompt = restartPrompt!!
                    AlertDialog(
                        onDismissRequest = {
                            restartPrompt = null
                            scope.launch {
                                snackbarHostState.showSnackbar(prompt.dismissMessage)
                            }
                        },
                        title = { Text(prompt.title) },
                        text = { Text(prompt.message) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    restartPrompt = null
                                    ModuleCommand.sendCommand(this@SettingActivity, "exitApp")
                                }
                            ) {
                                Text("立即重启")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    restartPrompt = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar(prompt.dismissMessage)
                                    }
                                }
                            ) {
                                Text("稍后")
                            }
                        }
                    )
                }

                SettingScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onSearchRequested = viewModel::enterSearch,
                    onSearchClosed = viewModel::exitSearch,
                    onIssueClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, false) },
                    onIssueLongClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, true) },
                    onSaveClick = {
                        if (!viewModel.hasPendingChanges) {
                            scope.launch {
                                snackbarHostState.showSnackbar("没有修改需要保存")
                            }
                            return@SettingScreen
                        }

                        viewModel.saveChanges()
                        restartPrompt = RestartPrompt.Save
                    },
                    onBackupRestoreClick = {
                        showBackupDialog = true
                    }
                )
                }
            }
        }
    }
}

private fun openUrlInDefaultBrowser(url: String, isSkip: Boolean) {
    if (!openTelegramChannel(isSkip)) {
        runCatching {
            if (!url.contains(TCQTBuild.OPEN_SOURCE)) {
                Toasts.error("尝试打开不支持的链接!")
                Log.e("尝试打开不受支持的链接: $url !!!")
                return
            }
            val uri = url.toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            HookEnv.hostAppContext.startActivity(intent)
        }.onFailure {
            Toasts.error("Failed to open url: $url")
            HookEnv.hostAppContext.copyToClipboard(url, false)
            Toasts.info("Url地址已复制到剪贴板,请手动访问.")
        }
    }
}

private fun openTelegramChannel(isSkip: Boolean): Boolean {
    if (isSkip) return false

    val tgIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "tg://resolve?domain=${TCQTBuild.TG_GROUP}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    return try {
        HookEnv.hostAppContext.startActivity(tgIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }
}
