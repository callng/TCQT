package com.owo233.tcqt.ui.settings

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.config.ThemeSettings
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.FriendHistoryEvent
import com.owo233.tcqt.host.service.FriendHistoryManager
import com.owo233.tcqt.host.service.FriendHistoryRecord
import com.owo233.tcqt.host.service.FriendHistorySnapshot
import com.owo233.tcqt.host.service.FriendHistoryStore
import com.owo233.tcqt.host.service.ManualAddResult
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.FriendAvatar
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.TCQTAlertDialog
import com.owo233.tcqt.ui.component.horizontalSafePadding
import com.owo233.tcqt.ui.component.isUinLike
import com.owo233.tcqt.ui.component.openOfficialUserPage
import com.owo233.tcqt.ui.theme.SettingTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 界面轮询上限；超过后不再等，交给「检测状态」展示真实结果。 */
private const val REFRESH_UI_MAX_WAIT_MS = 150_000L

class FriendHistoryActivity : BaseComposeActivity() {

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
                FriendHistoryScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun FriendHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val accountUin = remember {
        runCatching { QQInterfaces.currentUin }.getOrDefault("")
    }
    var refreshToken by remember { mutableIntStateOf(0) }
    val snapshot by produceState(
        initialValue = FriendHistorySnapshot(accountUin = accountUin),
        accountUin,
        refreshToken,
    ) {
        value = withContext(Dispatchers.IO) {
            if (accountUin.isBlank()) {
                FriendHistorySnapshot()
            } else {
                val loaded = FriendHistoryStore.load(accountUin)
                // 打开记录页即视为已读：清掉入口红点（内存里也同步成已读，避免本页再显示未读）。
                if (loaded.hasUnread) {
                    FriendHistoryStore.clearUnread(accountUin)
                    loaded.copy(unreadSince = 0)
                } else {
                    loaded
                }
            }
        }
    }

    // 记录开关只影响是否继续记录/提醒，不影响查看已有数据。
    val recordingEnabled = remember {
        runCatching { TCQTSetting.getValue<Boolean>("friend_history") ?: true }.getOrDefault(true)
    }

    var showManualDialog by remember { mutableStateOf(false) }
    var showExclusionDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FriendHistoryEvent?>(null) }
    // 正在打开资料页的 QQ 号：避免连点叠加多次宿主 Activity 启动。
    var openingUin by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshHint by remember { mutableStateOf("") }
    var statusToken by remember { mutableIntStateOf(0) }
    // 检测状态要读快照/刷新状态文件，必须放到 IO；主线程只接收结果。
    val statusText by produceState(
        initialValue = "正在读取检测状态…",
        accountUin,
        refreshToken,
        statusToken,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { FriendHistoryManager.statusText() }.getOrElse {
                "读取检测状态失败：${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = FriendHistoryStore.exportText(accountUin)
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选文件")
                }
            }
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "已导出到所选文件" },
                    onFailure = { "导出失败：${it.message ?: "请稍后重试"}" },
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取所选文件")
                    FriendHistoryStore.importText(accountUin, text)
                }
            }
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { it.summary },
                    onFailure = { "导入失败：${it.message ?: "不是有效的 JSON"}" },
                )
            )
            if (result.isSuccess) refreshToken++
        }
    }

    if (showManualDialog) {
        var adding by remember { mutableStateOf(false) }
        ManualAddDialog(
            adding = adding,
            onDismiss = { if (!adding) showManualDialog = false },
            onConfirm = { targetUin, input ->
                if (targetUin.length !in 5..12) {
                    scope.launch { snackbar.showSnackbar("QQ 号需为 5~12 位数字") }
                } else {
                    adding = true
                    scope.launch {
                        // 留空才自动查昵称；查询失败不阻塞添加，只在提示里说明。
                        val lookup = if (input.isBlank()) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    FriendHistoryManager.lookupManualNickname(accountUin, targetUin)
                                }.getOrDefault(FriendHistoryManager.ManualNickLookup())
                            }
                        } else {
                            FriendHistoryManager.ManualNickLookup()
                        }
                        val result = withContext(Dispatchers.IO) {
                            FriendHistoryStore.addManualEvent(
                                uin = accountUin,
                                targetUin = targetUin,
                                remark = input,
                                nick = lookup.displayName,
                            )
                        }
                        adding = false
                        when (result) {
                            ManualAddResult.ADDED -> {
                                showManualDialog = false
                                refreshToken++
                                snackbar.showSnackbar(
                                    when {
                                        input.isNotBlank() -> "已添加记录"
                                        lookup.displayName.isNotBlank() ->
                                            "已添加记录，已自动获取昵称：${lookup.displayName}"
                                        else -> "已添加记录；没有查询到昵称，将显示 QQ 号"
                                    }
                                )
                            }

                            ManualAddResult.DUPLICATE -> {
                                // 同一 QQ 号只保留一条记录：拒绝写入，也不覆盖原有备注与时间。
                                snackbar.showSnackbar("该 QQ 号已在记录中")
                            }

                            ManualAddResult.INVALID -> {
                                snackbar.showSnackbar("QQ 号格式不正确")
                            }
                        }
                    }
                }
            },
        )
    }

    pendingDelete?.let { target ->
        TCQTAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除记录",
            text = { Text("是否删除「${target.displayName}」这条记录？删除后立即生效。") },
            confirmButton = {
                ActionButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            FriendHistoryStore.removeEvent(accountUin, target)
                        }
                        refreshToken++
                        snackbar.showSnackbar(if (ok) "已删除记录" else "这条记录已不存在")
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                ActionButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showExclusionDialog) {
        ExclusionDialog(
            exclusions = snapshot.exclusions,
            onDismiss = { showExclusionDialog = false },
            onChange = { exclusions ->
                FriendHistoryStore.setExclusions(accountUin, exclusions)
                refreshToken++
            },
        )
    }

    if (showClearDialog) {
        TCQTAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = "清空历史记录",
            text = { Text("将删除全部 ${snapshot.events.size} 条记录，好友快照与排除名单会保留。") },
            confirmButton = {
                ActionButton(onClick = {
                    showClearDialog = false
                    FriendHistoryStore.clearEvents(accountUin)
                    refreshToken++
                }) { Text("清空") }
            },
            dismissButton = {
                ActionButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "历史好友",
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
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalSafePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            ) {
                if (!recordingEnabled) {
                    item(key = "recording_title") { SmallTitle(text = "状态") }
                    item(key = "recording_off") {
                        PreferenceSegment(first = true, last = true) {
                            BasicComponent(
                                title = "记录已暂停",
                                summary = "仍可查看已有数据；关闭期间不会写入新记录，也不会弹出被删提醒。" +
                                        "可在「设置 → 好友历史 → 好友历史记录」重新开启。",
                            )
                        }
                    }
                }

                item(key = "overview_title") { SmallTitle(text = "概览") }
                item(key = "overview") {
                    PreferenceSegment(first = true, last = true) {
                        BasicComponent(
                            title = "好友快照 ${snapshot.friends.count { it.value.present }} 位",
                            summary = buildString {
                                append("记录 ${snapshot.events.size} 条")
                                append(" · 同步于 ")
                                append(
                                    if (snapshot.lastSyncAt > 0) {
                                        FriendHistoryStore.relativeTime(snapshot.lastSyncAt)
                                    } else {
                                        "尚未同步"
                                    }
                                )
                                if (snapshot.exclusions.isNotEmpty()) {
                                    append(" · 排除 ${snapshot.exclusions.size} 人")
                                }
                                // 自动缓存自证：基线里确实存了头像地址（昵称/QQ 号见快照本身）。
                                val withAvatar = snapshot.friends.values.count { it.avatar.isNotBlank() }
                                append(" · 含头像 $withAvatar 条")
                            },
                        )
                    }
                }

                item(key = "detect_title") { SmallTitle(text = "检测状态") }
                item(key = "detect") {
                    PreferenceSegment(first = true, last = true) {
                        BasicComponent(
                            title = "检测链路",
                            summary = statusText,
                        )
                    }
                }

                item(key = "action_title") { SmallTitle(text = "操作") }
                item(key = "manual") {
                    PreferenceSegment(first = true, last = false) {
                        BasicComponent(
                            title = "手动添加记录",
                            summary = "为指定 QQ 号补一条记录，不改动好友快照",
                            onClick = { showManualDialog = true },
                        )
                    }
                }
                item(key = "exclusion") {
                    PreferenceSegment(first = false, last = false) {
                        BasicComponent(
                            title = "排除名单（${snapshot.exclusions.size}）",
                            summary = "同一份名单同时作用于记录和通知：被排除的人既不写记录，也不提醒",
                            onClick = { showExclusionDialog = true },
                        )
                    }
                }
                item(key = "refresh") {
                    PreferenceSegment(first = false, last = false) {
                        BasicComponent(
                            title = if (refreshing) "刷新中…" else "立即刷新好友列表",
                            summary = if (refreshing) {
                                refreshHint.ifBlank { "正在后台读取好友缓存…" }
                            } else {
                                "在后台直接读取宿主好友缓存并重新比对；失败不影响记录，等下次登录自动检测"
                            },
                            onClick = {
                                if (!refreshing) {
                                    refreshing = true
                                    refreshHint = "正在后台读取好友缓存…"
                                    // 取数放到模块的 IO 作用域：反射读全量好友（含逐个读字段）
                                    // 可能很慢甚至卡住，绝不能占用界面协程，否则「刷新中」无法退出。
                                    val result = CompletableDeferred<String>()
                                    ModuleScope.launchIO("好友刷新") {
                                        result.complete(
                                            runCatching { FriendHistoryManager.manualRefresh() }
                                                .getOrElse {
                                                    "刷新异常：${it.message ?: it.javaClass.simpleName}"
                                                }
                                        )
                                    }
                                    scope.launch {
                                        var message: String? = null
                                        val startedAt = SystemClock.elapsedRealtime()
                                        try {
                                            while (true) {
                                                delay(1000)
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        FriendHistoryManager.refreshProgress()
                                                    }
                                                }.getOrNull()?.let { progress ->
                                                    if (progress.active) {
                                                        refreshHint = progress.message
                                                    }
                                                }
                                                if (result.isCompleted) {
                                                    message = result.await()
                                                    break
                                                }
                                                if (SystemClock.elapsedRealtime() - startedAt >
                                                    REFRESH_UI_MAX_WAIT_MS
                                                ) {
                                                    message = "刷新超过 ${REFRESH_UI_MAX_WAIT_MS / 1000} 秒仍未返回，" +
                                                            "已停止等待；后台会继续写入结果，稍后看「检测状态」"
                                                    break
                                                }
                                            }
                                        } catch (error: kotlinx.coroutines.CancellationException) {
                                            throw error
                                        } catch (error: Throwable) {
                                            message = "刷新异常：${error.message ?: error.javaClass.simpleName}"
                                        } finally {
                                            // 唯一出口：任何异常/超时/正常结束都必须解除「刷新中」。
                                            refreshing = false
                                            refreshHint = ""
                                        }
                                        message?.let { snackbar.showSnackbar(it) }
                                        refreshToken++
                                        statusToken++
                                    }
                                }
                            },
                        )
                    }
                }
                item(key = "clear") {
                    PreferenceSegment(first = false, last = true) {
                        BasicComponent(
                            title = "清空历史记录",
                            summary = "只清空变动记录，保留好友快照与排除名单",
                            onClick = { showClearDialog = true },
                        )
                    }
                }

                item(key = "io_title") { SmallTitle(text = "导入与导出") }
                item(key = "export") {
                    PreferenceSegment(first = true, last = false) {
                        BasicComponent(
                            title = "导出 JSON",
                            summary = "可读 JSON，包含好友快照、变动记录与排除名单",
                            onClick = {
                                if (accountUin.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("当前账号未登录，无法导出") }
                                } else {
                                    exportLauncher.launch("tcqt_friend_history_$accountUin.json")
                                }
                            },
                        )
                    }
                }
                item(key = "import") {
                    PreferenceSegment(first = false, last = true) {
                        BasicComponent(
                            title = "导入 JSON",
                            summary = "逐条校验并合并；坏条目会跳过并计数，不会整包失败",
                            onClick = {
                                if (accountUin.isBlank()) {
                                    scope.launch { snackbar.showSnackbar("当前账号未登录，无法导入") }
                                } else {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }
                            },
                        )
                    }
                }

                item(key = "events_title") { SmallTitle(text = "好友变动记录") }
                if (snapshot.events.isEmpty()) {
                    item(key = "events_empty") {
                        EmptyStateCard(
                            icon = MiuixIcons.Folder,
                            title = "暂无记录",
                            summary = "好友新增或列表同步后会自动记录；也可以手动添加",
                        )
                    }
                } else {
                    itemsIndexed(
                        snapshot.events,
                        key = { index, event -> "${event.uin}_${event.detectedAt}_$index" },
                    ) { index, event ->
                        PreferenceSegment(
                            first = index == 0,
                            last = index == snapshot.events.lastIndex,
                        ) {
                            FriendEventRow(
                                event = event,
                                // 徽标按“当前状态”现场推导，而不是事件自己的来源文字：
                                // 被删后又加回来时，旧的删除记录也会跟着显示成「已恢复」。
                                status = snapshot.friends[event.uin]?.resolvedStatus,
                                onOpenProfile = { target ->
                                    when {
                                        // 打开过程中忽略连点，避免叠加多次宿主 Activity 启动。
                                        openingUin != null -> Unit
                                        !isUinLike(target.uin) -> scope.launch {
                                            snackbar.showSnackbar("QQ 号无效，无法打开资料卡")
                                        }
                                        else -> {
                                            // 主线程只做校验与分发：类加载、AllInOne 构造、
                                            // Intent 拼装都在 openOfficialUserPage 的 IO 段完成。
                                            openingUin = target.uin
                                            scope.launch {
                                                val preferSearch =
                                                    target.remark.isBlank() && target.nick.isBlank()
                                                val ok = runCatching {
                                                    openOfficialUserPage(
                                                        context,
                                                        target.uin,
                                                        preferSearch,
                                                    )
                                                }.getOrDefault(false)
                                                openingUin = null
                                                if (!ok) {
                                                    snackbar.showSnackbar(
                                                        "打不开官方页面：当前宿主缺少搜索/资料卡入口"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongPress = { pendingDelete = event },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendEventRow(
    event: FriendHistoryEvent,
    /** 该好友当前的状态（快照里没有这个人时为 null）。 */
    status: Int?,
    onOpenProfile: (FriendHistoryEvent) -> Unit,
    onLongPress: () -> Unit,
) {
    val sourceLabel = when (event.source) {
        FriendHistoryEvent.SOURCE_ACTIVE -> "主动删除"
        FriendHistoryEvent.SOURCE_MANUAL -> "手动添加"
        FriendHistoryEvent.SOURCE_ADDED -> "新增好友"
        else -> "同步发现"
    }
    val badge = when (status) {
        FriendHistoryRecord.STATUS_FRIEND -> "已恢复"
        FriendHistoryRecord.STATUS_ACTIVE_DELETED -> "已主动删除"
        else -> "已删除"
    }
    val badgeColor = if (status == FriendHistoryRecord.STATUS_FRIEND) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MiuixTheme.colorScheme.error
    }
    BasicComponent(
        modifier = Modifier.combinedClickable(
            onClick = { onOpenProfile(event) },
            onLongClick = onLongPress,
        ),
        title = event.displayName,
        startAction = { FriendAvatar(event.uin) },
        summary = buildString {
            append(event.uin)
            append(" · ")
            append(sourceLabel)
            append(" · ")
            // 有可靠检测窗口时显示区间（上次同步 ~ 本次），旧数据退化为单点。
            append(FriendHistoryStore.timeRangeText(event.timeRangeBegin, event.timeRangeEnd))
            if (event.source == FriendHistoryEvent.SOURCE_SYNC && event.seenAt > 0) {
                append(" · 最后在线 ")
                append(FriendHistoryStore.absoluteTime(event.seenAt))
            }
        },
        endActions = {
            Text(
                text = badge,
                style = MiuixTheme.textStyles.body2,
                color = badgeColor,
            )
        },
    )
}

@Composable
private fun ManualAddDialog(
    adding: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetUin: String, remark: String) -> Unit,
) {
    var inputUin by remember { mutableStateOf("") }
    var inputRemark by remember { mutableStateOf("") }

    TCQTAlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = "手动添加记录",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = inputUin,
                    onValueChange = { value -> inputUin = value.filter(Char::isDigit).take(12) },
                    label = "好友 QQ 号",
                    useLabelAsPlaceholder = true,
                    cornerRadius = 14.dp,
                )
                TextField(
                    value = inputRemark,
                    onValueChange = { inputRemark = it },
                    label = "备注或昵称（留空自动查询）",
                    useLabelAsPlaceholder = true,
                    cornerRadius = 14.dp,
                )
                if (adding) {
                    Text(
                        text = "正在查询昵称…",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        },
        confirmButton = {
            ActionButton(
                onClick = { if (!adding) onConfirm(inputUin, inputRemark) },
                enabled = !adding,
            ) { Text(if (adding) "添加中…" else "添加") }
        },
        dismissButton = {
            ActionButton(onClick = onDismiss, enabled = !adding) { Text("取消") }
        },
    )
}

@Composable
private fun ExclusionDialog(
    exclusions: List<String>,
    onDismiss: () -> Unit,
    onChange: (List<String>) -> Unit,
) {
    var inputUin by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(exclusions) }

    TCQTAlertDialog(
        onDismissRequest = onDismiss,
        title = "排除名单",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "被排除的人不会被写入历史记录，也不会弹出被删通知。记录与通知共用这一份名单。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextField(
                    value = inputUin,
                    onValueChange = { value -> inputUin = value.filter(Char::isDigit).take(12) },
                    label = "好友 QQ 号",
                    useLabelAsPlaceholder = true,
                    cornerRadius = 14.dp,
                )
                ActionButton(
                    onClick = {
                        if (inputUin.isNotBlank()) {
                            if (inputUin !in current) {
                                current = current + inputUin
                                onChange(current)
                            }
                            inputUin = ""
                        }
                    }
                ) {
                    Text("添加")
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (current.isEmpty()) {
                        Text(
                            text = "名单为空",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    current.forEach { uin ->
                        BasicComponent(
                            title = uin,
                            endActions = {
                                IconButton(onClick = {
                                    current = current - uin
                                    onChange(current)
                                }) {
                                    Icon(
                                        MiuixIcons.Delete,
                                        contentDescription = "移除",
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            ActionButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
