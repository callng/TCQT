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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.func.ModuleCommand
import com.owo233.tcqt.internals.setting.ThemeSettings
import com.owo233.tcqt.ui.miuix.AlertDialog
import com.owo233.tcqt.ui.miuix.MaterialTheme
import com.owo233.tcqt.ui.miuix.TextButton
import com.owo233.tcqt.utils.ConfigBackupManager
import com.owo233.tcqt.utils.dexkit.DexKitCache
import com.owo233.tcqt.utils.log.BugReportExporter
import com.owo233.tcqt.utils.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

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
                }
                startActivity(intent)
            }.onFailure {
                Toasts.error("Failed to open url: $url")
                copyToClipboard(url, false)
                Toasts.info("Url地址已复制到剪贴板,请手动访问.")
            }
        }
    }

    private fun openTelegramChannel(isSkip: Boolean): Boolean {
        if (isSkip) return false

        val tgIntent = Intent(Intent.ACTION_VIEW).apply {
            data = "tg://resolve?domain=${TCQTBuild.TG_GROUP}".toUri()
        }

        return try {
            startActivity(tgIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var moduleThemeMode by remember { mutableStateOf(ThemeSettings.themeMode) }
            var monetEnabled by remember { mutableStateOf(ThemeSettings.monetEnabled) }
            val resolvedDarkTheme = moduleThemeMode.resolveDark(isDarkTheme)

            SideEffect {
                updateStatusBarAppearance(resolvedDarkTheme)
            }

            SettingTheme(
                themeMode = moduleThemeMode,
                monetEnabled = monetEnabled,
                systemDarkTheme = isDarkTheme,
            ) {
                val viewModel: SettingViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                var restartPrompt by rememberSaveable { mutableStateOf<RestartPrompt?>(null) }
                var showClearDialog by rememberSaveable { mutableStateOf(false) }
                var showBackupDialog by rememberSaveable { mutableStateOf(false) }
                var showDexKitClearDialog by rememberSaveable { mutableStateOf(false) }
                var showBugReportDialog by rememberSaveable { mutableStateOf(false) }
                var isExportingBugReport by remember { mutableStateOf(false) }

                DisposableEffect(viewModel) {
                    onRestoreSuccessCallback = {
                        viewModel.reloadPersistedSettings()
                        viewModel.recalculateStats()
                        moduleThemeMode = ThemeSettings.themeMode
                        monetEnabled = ThemeSettings.monetEnabled
                        restartPrompt = RestartPrompt.Restore
                    }
                    onDispose {
                        onRestoreSuccessCallback = null
                    }
                }

                BackHandler {
                    when {
                        viewModel.isSearchActive -> viewModel.exitSearch()
                        viewModel.isErrorOverviewActive -> viewModel.navigateUp()
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
                                        moduleThemeMode = ThemeSettings.themeMode
                                        monetEnabled = ThemeSettings.monetEnabled
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
                        BackupRestoreDialog(
                            onDismissRequest = { showBackupDialog = false },
                            onBackup = {
                                showBackupDialog = false
                                startBackup()
                            },
                            onBackupWithNewDirectory = {
                                showBackupDialog = false
                                startBackup(forceChooseDirectory = true)
                            },
                            onRestore = {
                                showBackupDialog = false
                                restoreFilePicker.launch(arrayOf("application/json"))
                            },
                            onClearDexKit = {
                                showBackupDialog = false
                                showDexKitClearDialog = true
                            },
                            onClearSettings = {
                                showBackupDialog = false
                                showClearDialog = true
                            },
                        )
                    }

                    if (showDexKitClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showDexKitClearDialog = false },
                            title = { Text("清空 DexKit 缓存") },
                            text = { Text("是否清空 DexKit 缓存并重新查找？") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDexKitClearDialog = false
                                        DexKitCache.clearCache()
                                        ModuleCommand.sendCommand(this@SettingActivity, "restart")
                                    }
                                ) {
                                    Text("清空")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDexKitClearDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }

                    if (showBugReportDialog) {
                        AlertDialog(
                            onDismissRequest = { showBugReportDialog = false },
                            title = { Text("导出异常报告") },
                            text = {
                                Text(
                                    "将打包当前 ${viewModel.errorCount} 个异常功能的全部进程日志，" +
                                        "并附带宿主版本、模块版本、Android 版本、设备型号和系统指纹等诊断信息。" +
                                        "\n\n异常堆栈可能包含运行路径或上下文信息。是否继续并打开系统分享面板？"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showBugReportDialog = false
                                        isExportingBugReport = true
                                        scope.launch {
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    BugReportExporter.export()
                                                }
                                            }
                                            isExportingBugReport = false

                                            val report = result.getOrNull()
                                            if (report == null) {
                                                val message = result.exceptionOrNull()
                                                    ?.message
                                                    ?.lineSequence()
                                                    ?.firstOrNull()
                                                    ?.takeIf(String::isNotBlank)
                                                    ?: "未知错误"
                                                snackbarHostState.showSnackbar("异常报告打包失败：$message")
                                                return@launch
                                            }

                                            runCatching {
                                                val shareIntent =
                                                    BugReportExporter.createShareIntent(report)
                                                BugReportExporter.grantSharePermissions(
                                                    this@SettingActivity,
                                                    shareIntent,
                                                    report.shareUri,
                                                )
                                                startActivity(
                                                    Intent.createChooser(
                                                        shareIntent,
                                                        "分享异常报告",
                                                    ),
                                                )
                                            }.onFailure { throwable ->
                                                val message = throwable.message
                                                    ?.lineSequence()
                                                    ?.firstOrNull()
                                                    ?.takeIf(String::isNotBlank)
                                                    ?: "没有可用的分享应用"
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "报告已生成，但无法打开分享：$message"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("继续")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBugReportDialog = false }) {
                                    Text("取消")
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
                                        ModuleCommand.sendCommand(this@SettingActivity, "restart")
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
                        themeMode = moduleThemeMode,
                        monetEnabled = monetEnabled,
                        onThemeModeChange = { mode ->
                            ThemeSettings.setThemeMode(mode)
                            moduleThemeMode = mode
                        },
                        onMonetEnabledChange = { enabled ->
                            ThemeSettings.monetEnabled = enabled
                            monetEnabled = enabled
                        },
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
                        },
                        isExportingBugReport = isExportingBugReport,
                        onExportBugReportClick = {
                            if (!isExportingBugReport) {
                                showBugReportDialog = true
                            }
                        },
                        onFeatureClick = { key ->
                            val handled = ActionManager.getActionByKey(key)?.onUiClick(this@SettingActivity) == true
                            if (!handled) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("该功能没有可打开的页面")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreDialog(
    onDismissRequest: () -> Unit,
    onBackup: () -> Unit,
    onBackupWithNewDirectory: () -> Unit,
    onRestore: () -> Unit,
    onClearDexKit: () -> Unit,
    onClearSettings: () -> Unit,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "备份与还原",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "配置管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BackupActionRow(
                        icon = MiuixIcons.Backup,
                        title = "备份模块设置",
                        summary = "导出当前配置；长按可重选目录",
                        onClick = onBackup,
                        onLongClick = onBackupWithNewDirectory,
                    )
                    BackupActionDivider()
                    BackupActionRow(
                        icon = MiuixIcons.Reset,
                        title = "还原模块设置",
                        summary = "从备份文件覆盖当前配置",
                        onClick = onRestore,
                    )
                    BackupActionDivider()
                    BackupActionRow(
                        icon = MiuixIcons.Refresh,
                        title = "清空 DexKit 缓存",
                        summary = "Hook 失效时重新识别宿主方法",
                        onClick = onClearDexKit,
                    )
                    BackupActionDivider()
                    BackupActionRow(
                        icon = MiuixIcons.Delete,
                        title = "清空模块设置",
                        summary = "删除全部配置并恢复默认行为",
                        onClick = onClearSettings,
                        destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val accent = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    ArrowPreference(
        title = title,
        summary = summary,
        titleColor = BasicComponentDefaults.titleColor(
            color = if (destructive) accent else MaterialTheme.colorScheme.onSurface,
        ),
        startAction = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                contentColor = accent,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

@Composable
private fun BackupActionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
    )
}
