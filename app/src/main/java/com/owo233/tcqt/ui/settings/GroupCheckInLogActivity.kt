package com.owo233.tcqt.ui.settings

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.ThemeSettings
import com.owo233.tcqt.features.checkin.GroupCheckIn
import com.owo233.tcqt.host.service.GroupCheckInDayLog
import com.owo233.tcqt.host.service.GroupCheckInLog
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.TCQTAlertDialog
import com.owo233.tcqt.ui.component.horizontalSafePadding
import com.owo233.tcqt.ui.theme.SettingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 群打卡监控面板：按「已勾选要打卡的群」统计完成度，并区分失败/未执行。 */
class GroupCheckInLogActivity : BaseComposeActivity() {

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
                GroupCheckInLogScreen(onBack = ::finish)
            }
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 面板一次加载的全部数据：今日记录（含失败）+ 打卡范围快照。 */
private data class MonitorData(
    val log: GroupCheckInDayLog = GroupCheckInDayLog(),
    val monitor: GroupCheckIn.MonitorSnapshot? = null,
)

@Composable
private fun GroupCheckInLogScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var refreshToken by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(MonitorData()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 点「刷新」只改 refreshToken，重新读文件与打卡范围，不退出页面。
    LaunchedEffect(refreshToken) {
        refreshing = true
        try {
            data = withContext(Dispatchers.IO) {
                MonitorData(
                    log = runCatching { GroupCheckInLog.today() }.getOrDefault(GroupCheckInDayLog()),
                    monitor = runCatching { GroupCheckIn.monitorSnapshot() }.getOrNull(),
                )
            }
        } finally {
            refreshing = false
        }
    }

    val log = data.log
    val monitor = data.monitor
    val targets = monitor?.targets.orEmpty()
    val targetUins = remember(targets) { targets.map { it.uin }.toSet() }
    val doneUins = monitor?.completed ?: log.records.map { it.uin }.toSet()
    val total = targets.size
    val done = targets.count { it.uin in doneUins }
    val remaining = targets.filter { it.uin !in doneUins }
    val failures = log.failures.associateBy { it.uin }
    val failed = remaining.mapNotNull { target -> failures[target.uin]?.let { target to it } }
    val notRun = remaining.filter { it.uin !in failures }
    val doneItems = targets.filter { it.uin in doneUins }.map { target ->
        target to log.records.firstOrNull { it.uin == target.uin }
    }
    val extraRecords = log.records.filter { it.uin !in targetUins }.sortedByDescending { it.at }
    val scopeError = monitor?.error.orEmpty()

    val headline = if (monitor == null) "无法读取打卡范围" else "$done/$total"
    val statText = buildString {
        when {
            monitor == null -> append("读取打卡范围失败，请点右上角刷新")
            total == 0 -> append(scopeError.ifBlank { "当前打卡范围里没有群" })
            remaining.isEmpty() -> append("今天已全部打完（$total 个群）")
            else -> {
                append("还差 ${remaining.size} 个：")
                append("打卡失败 ${failed.size} 个、未执行 ${notRun.size} 个")
                if (extraRecords.isNotEmpty()) {
                    append("；另有 ${extraRecords.size} 个已打卡群不在当前勾选")
                }
            }
        }
        if (scopeError.isNotBlank() && total > 0) {
            append("；")
            append(scopeError)
        }
        append("；日期 ")
        append(log.date.ifBlank { "-" })
    }
    val scopeText = when {
        monitor == null -> "读取失败"
        monitor.targetAll -> "全部已加入群 · 目标 $total 个"
        else -> "指定群号 · 目标 $total 个"
    }
    // 「最近一次执行」与版本/日志信息：不依赖外部文件，页面里就能自证。
    val lastRunAt = monitor?.lastRunAt.orEmpty()
    val lastRunResult = monitor?.lastRunResult.orEmpty()
    val lastRunNotify = monitor?.lastRunNotify.orEmpty()
    val moduleVersion = monitor?.moduleVersion.orEmpty()
    val logPath = monitor?.logPath.orEmpty()
    val logError = monitor?.logError.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "今日打卡记录",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!refreshing) refreshToken++ },
                    ) {
                        Icon(MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
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
                item(key = "summary_title") { SmallTitle(text = "今日概览") }
                item(key = "summary") {
                    PreferenceSegment(first = true, last = false) {
                        BasicComponent(
                            title = headline,
                            summary = statText,
                            onClick = { if (!refreshing) refreshToken++ },
                        )
                    }
                }
                item(key = "scope") {
                    PreferenceSegment(first = false, last = true) {
                        BasicComponent(
                            title = "打卡范围",
                            summary = "$scopeText；分母取当前勾选，分子取今日成功记录",
                        )
                    }
                }

                // 不依赖外部日志文件：最近一次点击的时间、结论、通知到底有没有落地，直接显示在这里。
                item(key = "last_run_title") { SmallTitle(text = "最近一次执行") }
                item(key = "last_run") {
                    PreferenceSegment(first = true, last = false) {
                        BasicComponent(
                            title = lastRunAt.ifBlank { "还没有执行记录" },
                            summary = if (lastRunAt.isBlank()) {
                                "点一次「立即执行」后，这里会显示判定结论与通知投递结果"
                            } else {
                                "$lastRunResult\n通知：$lastRunNotify"
                            },
                        )
                    }
                }
                item(key = "version_log") {
                    PreferenceSegment(first = false, last = true) {
                        BasicComponent(
                            title = "版本与日志",
                            summary = buildString {
                                append("模块 ").append(moduleVersion.ifBlank { "未知" })
                                append("；日志：").append(logPath.ifBlank { "尚未写入" })
                                if (logError.isNotBlank()) {
                                    append("\n日志写入失败：").append(logError)
                                }
                            },
                        )
                    }
                }

                item(key = "action_title") { SmallTitle(text = "操作") }
                item(key = "refresh") {
                    PreferenceSegment(first = true, last = true) {
                        BasicComponent(
                            title = if (refreshing) "刷新中…" else "刷新记录",
                            summary = "重新读取今天的打卡记录与打卡范围，不用退出页面",
                            onClick = { if (!refreshing) refreshToken++ },
                        )
                    }
                }

                // 单独的“重置”分组，和刷新分开，避免想刷新时误点删除。
                item(key = "reset_title") { SmallTitle(text = "重置今日记录") }
                item(key = "clear") {
                    PreferenceSegment(first = true, last = true) {
                        BasicComponent(
                            title = "删除今日记录",
                            summary = "清空今天已打卡与失败的记录；之后点执行会重新打卡。会先弹确认",
                            onClick = { if (!clearing && !refreshing) showClearDialog = true },
                        )
                    }
                }

                if (failed.isNotEmpty()) {
                    item(key = "failed_title") { SmallTitle(text = "打卡失败（${failed.size}）") }
                    itemsIndexed(
                        failed,
                        key = { _, item -> "failed_${item.first.uin}" },
                    ) { index, item ->
                        val (target, failure) = item
                        PreferenceSegment(
                            first = index == 0,
                            last = index == failed.lastIndex,
                        ) {
                            BasicComponent(
                                title = target.name.ifBlank { "群 ${target.uin}" },
                                summary = "群号 ${target.uin} · ${failure.reason.ifBlank { "打卡失败" }}" +
                                        " · ${timeText(failure.at)}",
                            )
                        }
                    }
                }

                if (notRun.isNotEmpty()) {
                    item(key = "notrun_title") { SmallTitle(text = "未执行（${notRun.size}）") }
                    itemsIndexed(
                        notRun,
                        key = { _, target -> "notrun_${target.uin}" },
                    ) { index, target ->
                        PreferenceSegment(
                            first = index == 0,
                            last = index == notRun.lastIndex,
                        ) {
                            BasicComponent(
                                title = target.name.ifBlank { "群 ${target.uin}" },
                                summary = "群号 ${target.uin} · 已勾选，今天还没有成功记录",
                            )
                        }
                    }
                }

                item(key = "done_title") { SmallTitle(text = "已打卡的群（${doneItems.size}）") }
                if (doneItems.isEmpty()) {
                    item(key = "done_empty") {
                        PreferenceSegment(first = true, last = true) {
                            BasicComponent(
                                title = "今天还没有打卡记录",
                                summary = "执行一次群打卡后，这里会按群号列出成功记录与时间。",
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        doneItems,
                        key = { _, item -> "done_${item.first.uin}" },
                    ) { index, item ->
                        val (target, record) = item
                        PreferenceSegment(
                            first = index == 0,
                            last = index == doneItems.lastIndex,
                        ) {
                            BasicComponent(
                                title = record?.name?.takeIf { it.isNotBlank() }
                                    ?: target.name.ifBlank { "群 ${target.uin}" },
                                summary = "群号 ${target.uin} · " +
                                        if (record != null) timeText(record.at) else "今日成功状态（无落盘时间）",
                            )
                        }
                    }
                }

                if (extraRecords.isNotEmpty()) {
                    item(key = "extra_title") {
                        SmallTitle(text = "今日已打卡但不在当前勾选（${extraRecords.size}）")
                    }
                    itemsIndexed(
                        extraRecords,
                        key = { _, record -> "extra_${record.uin}" },
                    ) { index, record ->
                        PreferenceSegment(
                            first = index == 0,
                            last = index == extraRecords.lastIndex,
                        ) {
                            BasicComponent(
                                title = record.name.ifBlank { "群 ${record.uin}" },
                                summary = "群号 ${record.uin} · ${timeText(record.at)} · 不计入上面的完成度",
                            )
                        }
                    }
                }

                item(key = "hint") {
                    BasicComponent(
                        title = "说明",
                        summary = "左边是今天已成功打卡的群数，右边是当前勾选要打卡的群数；" +
                                "失败记录保留到该群下次打卡成功为止，换天后自动清空。",
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        TCQTAlertDialog(
            onDismissRequest = { if (!clearing) showClearDialog = false },
            title = "删除今日记录",
            text = {
                Text(
                    "清空后今天已打卡的群会被当作未打卡，再次执行会重新打卡。" +
                            "只删除今天的打卡记录（含失败记录），群号、打卡方式、触发时间等设置不受影响。"
                )
            },
            confirmButton = {
                ActionButton(onClick = {
                    showClearDialog = false
                    clearing = true
                    scope.launch {
                        // 清空记录文件 + 配置里的今日成功状态，再重读面板，完成度会立刻归零。
                        withContext(Dispatchers.IO) {
                            runCatching { GroupCheckIn.clearTodayProgress() }
                        }
                        clearing = false
                        refreshToken++
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                ActionButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun timeText(epochSec: Long): String {
    if (epochSec <= 0) return "--:--"
    return runCatching {
        Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
    }.getOrDefault("--:--")
}
