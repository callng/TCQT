package com.owo233.tcqt.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import com.owo233.tcqt.core.action.ActionRegistry
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.config.ConfigBackupManager
import com.owo233.tcqt.core.config.ThemeSettings
import com.owo233.tcqt.core.dexkit.DexKitCache
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.env.copyToClipboard
import com.owo233.tcqt.core.log.BugReportExporter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.TCQTAlertDialog
import com.owo233.tcqt.ui.settings.model.RestartPrompt
import com.owo233.tcqt.ui.theme.SettingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class SettingActivity : BaseComposeActivity() {

    private val settingsModel by lazy { ViewModelProvider(this)[SettingViewModel::class.java] }

    private val pngIconPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            settingsModel.importPngIcon(contentResolver, uri)
        }

    private var onRestoreSuccessCallback: (() -> Unit)? = null

    private val backupDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val data = result.data ?: return@registerForActivityResult
            val uri = data.data ?: return@registerForActivityResult

            val takeFlags =
                data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            runCatching {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
                .onFailure { e ->
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
                } catch (_: Exception) {}
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
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
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
                val intent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                startActivity(intent)
            }
                .onFailure {
                    Toasts.error("Failed to open url: $url")
                    copyToClipboard(url, false)
                    Toasts.info("Url地址已复制到剪贴板,请手动访问.")
                }
        }
    }

    private fun openTelegramChannel(isSkip: Boolean): Boolean {
        if (isSkip) return false

        val tgIntent =
            Intent(Intent.ACTION_VIEW).apply {
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
        val createdAt = android.os.SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)

        val launchStarted = intent.getLongExtra(ModuleComponents.SETTINGS_LAUNCH_STARTED, 0L)
        if (savedInstanceState == null && launchStarted > 0L) {
            window.decorView.doOnPreDraw {
                val readyAt = android.os.SystemClock.elapsedRealtime()
                Log.i(
                    "[SettingsStartup] activity=${createdAt - launchStarted}ms, " +
                        "content=${readyAt - createdAt}ms, total=${readyAt - launchStarted}ms"
                )
            }
        }

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
                val viewModel = settingsModel
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
                        viewModel.isAppearanceActive -> viewModel.navigateUp()
                        viewModel.detailFeatureKey != null -> viewModel.navigateUp()
                        viewModel.isSearchActive -> viewModel.exitSearch()
                        viewModel.isErrorOverviewActive -> viewModel.navigateUp()
                        !viewModel.isAtRoot -> viewModel.navigateUp()
                        else -> finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MiuixTheme.colorScheme.surface,
                ) {
                    if (showClearDialog) {
                        TCQTAlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = "清空配置",
                            text = { Text("是否清空所有模块配置？该操作不可逆，清空后将恢复为默认值。") },
                            confirmButton = {
                                ActionButton(
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
                                ActionButton(onClick = { showClearDialog = false }) {
                                    Text("取消")
                                }
                            },
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
                        TCQTAlertDialog(
                            onDismissRequest = { showDexKitClearDialog = false },
                            title = "清空 DexKit 缓存",
                            text = { Text("将删除 debug/release 的普通、热更新及旧版 DexKit 缓存，并重启宿主重新查找。") },
                            confirmButton = {
                                ActionButton(
                                    onClick = {
                                        showDexKitClearDialog = false
                                        if (DexKitCache.clearCache()) {
                                            ModuleCommandBus.sendCommand(
                                                this@SettingActivity,
                                                ModuleCommandBus.CMD_RESTART,
                                            )
                                        } else {
                                            Toasts.error("清空 DexKit 缓存失败，请查看日志")
                                        }
                                    }
                                ) {
                                    Text("清空")
                                }
                            },
                            dismissButton = {
                                ActionButton(onClick = { showDexKitClearDialog = false }) {
                                    Text("取消")
                                }
                            },
                        )
                    }

                    if (showBugReportDialog) {
                        TCQTAlertDialog(
                            onDismissRequest = { showBugReportDialog = false },
                            title = "导出异常报告",
                            text = {
                                Text(
                                    "将打包当前 ${viewModel.errorCount} 个异常功能的全部进程日志，" +
                                        "并附带宿主版本、模块版本、Android 版本、设备型号和系统指纹等诊断信息。" +
                                        "\n\n异常堆栈可能包含运行路径或上下文信息。是否继续并打开系统分享面板？"
                                )
                            },
                            confirmButton = {
                                ActionButton(
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
                                                val message =
                                                    result
                                                        .exceptionOrNull()
                                                        ?.message
                                                        ?.lineSequence()
                                                        ?.firstOrNull()
                                                        ?.takeIf(String::isNotBlank) ?: "未知错误"
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
                                                    )
                                                )
                                            }
                                                .onFailure { throwable ->
                                                    val message =
                                                        throwable.message
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
                                ActionButton(onClick = { showBugReportDialog = false }) {
                                    Text("取消")
                                }
                            },
                        )
                    }

                    if (restartPrompt != null) {
                        val prompt = restartPrompt!!
                        TCQTAlertDialog(
                            onDismissRequest = {
                                restartPrompt = null
                                scope.launch {
                                    snackbarHostState.showSnackbar(prompt.dismissMessage)
                                }
                            },
                            title = prompt.title,
                            text = { Text(prompt.message) },
                            confirmButton = {
                                ActionButton(
                                    onClick = {
                                        restartPrompt = null
                                        ModuleCommandBus.sendCommand(
                                            this@SettingActivity,
                                            ModuleCommandBus.CMD_RESTART,
                                        )
                                    }
                                ) {
                                    Text("立即重启")
                                }
                            },
                            dismissButton = {
                                ActionButton(
                                    onClick = {
                                        restartPrompt = null
                                        scope.launch {
                                            snackbarHostState.showSnackbar(prompt.dismissMessage)
                                        }
                                    }
                                ) {
                                    Text("稍后")
                                }
                            },
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
                        onBack = { if (!viewModel.navigateUp()) finish() },
                        onSearchRequested = viewModel::enterSearch,
                        onSearchClosed = viewModel::exitSearch,
                        onIssueClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, false) },
                        onIssueLongClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, true) },
                        onSaveClick = {
                            if (viewModel.isImportingPngIcon) {
                                scope.launch { snackbarHostState.showSnackbar("请等待 PNG 图片读取完成") }
                                return@SettingScreen
                            }
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
                        onSelectPngIcon = { key ->
                            if (viewModel.beginPngIconSelection(key)) {
                                try {
                                    pngIconPicker.launch(arrayOf("image/png"))
                                } catch (e: Exception) {
                                    viewModel.importPngIcon(contentResolver, null)
                                    Toasts.error("无法打开图片选择器")
                                    Log.w("Failed to launch PNG icon picker", e)
                                }
                            }
                        },
                        isExportingBugReport = isExportingBugReport,
                        onExportBugReportClick = {
                            if (!isExportingBugReport) {
                                showBugReportDialog = true
                            }
                        },
                        onFeatureClick = { key ->
                            val handled =
                                ActionRegistry.getActionByKey(key)
                                    ?.onUiClick(this@SettingActivity) == true
                            if (!handled) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("该功能没有可打开的页面")
                                }
                            }
                        },
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
        title = "备份与还原",
        show = true,
        onDismissRequest = onDismissRequest,
        insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 24.dp),
    ) {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            BackupActionRow(MiuixIcons.Backup, "备份模块设置", "导出当前配置", onBackup)
            BackupActionRow(MiuixIcons.Folder, "选择备份目录", "更换目录并备份当前配置", onBackupWithNewDirectory)
            BackupActionRow(MiuixIcons.Reset, "还原模块设置", "从备份文件覆盖当前配置", onRestore)
            BackupActionRow(MiuixIcons.Refresh, "清空 DexKit 缓存", "Hook 失效时重新识别宿主方法", onClearDexKit)
            BackupActionRow(
                MiuixIcons.Delete,
                "清空模块设置",
                "删除全部配置并恢复默认行为",
                onClearSettings,
                destructive = true,
            )
        }
    }
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val accent = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    ArrowPreference(
        title = title,
        summary = summary,
        titleColor = BasicComponentDefaults.titleColor(color = accent),
        startAction = { Icon(imageVector = icon, contentDescription = null, tint = accent) },
        onClick = onClick,
    )
}
