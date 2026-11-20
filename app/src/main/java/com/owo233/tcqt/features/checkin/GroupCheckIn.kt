package com.owo233.tcqt.features.checkin

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQToastCompat
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.CheckInDiag
import com.owo233.tcqt.host.service.CheckInNotifier
import com.owo233.tcqt.host.service.GroupCheckInDayLog
import com.owo233.tcqt.host.service.GroupCheckInLog
import com.owo233.tcqt.host.service.api.GroupSignInService
import com.owo233.tcqt.host.service.api.parseGroupNumbers
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 群打卡。
 *
 * 行为对齐 XAutoDaily 的群签到：按群执行原生签到（OIDB 0xeb7），并按天去重；
 * 另外提供“群消息打卡”模式，用于发送自定义打卡文本。
 */
@RegisterAction
object GroupCheckIn : Feature(
    key = "group_check_in",
    name = "群打卡",
    desc = "每天对指定群执行签到，或向群发送自定义打卡消息。",
    uiOrder = 10,
    actionLabel = "立即执行一次",
) {

    private const val TAG = "GroupCheckIn"
    private const val TICK_MS = 30_000L
    private const val FIRST_DELAY_MS = 10_000L
    private const val RETRY_DELAY_MS = 1_500L

    /** running 超过这个时长仍未复位，视为上一轮卡死，允许手动点击强制重跑。 */
    private const val STUCK_TIMEOUT_MS = 30 * 60 * 1000L

    private const val TARGET_LIST = 1
    private const val TARGET_ALL = 2

    private const val MODE_NATIVE = 1
    private const val MODE_MESSAGE = 2

    private const val TRIGGER_DAILY = 1
    private const val TRIGGER_STARTUP = 2

    /** 单条通知模式下逐群刷新正文的最小间隔：群多时也不至于每群都刷一次。 */
    private const val PROGRESS_REFRESH_GAP_MS = 1_200L

    /** 上一次刷新进度正文的时刻（elapsedRealtime），用于节流。 */
    @Volatile
    private var lastProgressRefreshAt = 0L

    private val targetMode by intOption(
        "target_mode",
        "打卡范围",
        defaultValue = TARGET_LIST,
        desc = "选择“全部已加入群”时优先读取宿主群列表，读取失败则回退到下方群号。",
        options = listOf("指定群号", "全部已加入群"),
    )

    private val groups by groupListOption(
        "groups",
        "群号列表",
        desc = "从 QQ 群列表勾选需要打卡的群；存储格式为逗号分隔的群号。",
    )

    private val mode by intOption(
        "mode",
        "打卡方式",
        defaultValue = MODE_NATIVE,
        desc = "原生群签到使用 QQ 的群签到协议；群消息打卡会向群发送下方文本。",
        options = listOf("原生群签到（推荐）", "群消息打卡"),
    )

    private val command by stringOption(
        "command",
        "打卡内容",
        defaultValue = "打卡",
        desc = "仅“群消息打卡”模式使用；留空时发送“打卡”。",
        placeholder = "打卡",
    )

    private val trigger by intOption(
        "trigger",
        "触发方式",
        defaultValue = TRIGGER_DAILY,
        options = listOf("每天定时", "启动后自动", "仅手动"),
    )

    private val time by stringOption(
        "time",
        "每日打卡时间",
        defaultValue = "08:00",
        desc = "24 小时制，格式 HH:mm；仅“每天定时”生效。",
        placeholder = "08:00",
    )

    private val retry by intOption(
        "retry",
        "失败重试",
        defaultValue = 3,
        options = listOf("不重试", "重试 1 次", "重试 2 次", "重试 3 次"),
    )

    private val interval by sliderOption(
        "interval",
        "群间隔",
        defaultValue = 3,
        desc = "每个群之间的等待时间，避免请求过快。",
        min = 1,
        max = 30,
        suffix = "秒",
    )

    /** 今日已成功的群号，格式：yyyy-MM-dd|群号,群号。 */
    private var successState by stringOption("success_state", "今日成功记录", isHide = true)

    /** 今日是否已自动执行过，避免定时触发反复重试。 */
    private var attemptDay by stringOption("attempt_day", "自动执行日期", isHide = true)

    /** 上次执行结果，展示在设置页说明里。 */
    private var lastResult by stringOption("last_result", "上次执行结果", isHide = true)

    /** 最近一次执行记录：时间 / 结论 / 通知投递结果，直接展示在「今日打卡记录」页。 */
    private var lastRunAt by stringOption("last_run_at", "最近一次执行时间", isHide = true)
    private var lastRunResult by stringOption("last_run_result", "最近一次执行结论", isHide = true)
    private var lastRunNotify by stringOption("last_run_notify", "最近一次通知投递", isHide = true)

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var started = false

    @Volatile
    private var reviewStarted = false

    @Volatile
    private var running = false

    /** running 置位时刻，用于识别“上一轮卡死但没复位”。 */
    @Volatile
    private var runningSince = 0L

    @Volatile
    private var startupPending = false

    private val tickRunner = object : Runnable {
        override fun run() {
            try {
                checkSchedule()
            } finally {
                if (started) handler.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun install() {
        if (started) return
        started = true
        startupPending = true
        Log.i("群打卡已加载，触发方式：${trigger}")
        handler.postDelayed(tickRunner, FIRST_DELAY_MS)
    }

    override fun onUiClick(context: Context): Boolean {
        val clickAt = System.currentTimeMillis()
        // 第一条提示必须出现在任何耗时操作之前。
        // 之后的判定（读今日记录、读群列表、逐群比对）全部在后台做，
        // 命中“今天已全部完成”时第二条提示才能紧跟着出来。
        QQToastCompat.info("开始执行群打卡…")
        ModuleScope.launchIO("群打卡点击") {
            try {
                handleManualClick(clickAt)
            } catch (t: Throwable) {
                Log.e("群打卡：点击处理异常", t)
                diag("点击异常：${t.javaClass.simpleName}：${t.message}")
                // 回执本身也要兜住：曾经因为 timeLabel 抛异常，异常分支又抛一次，结束通知彻底没发出去。
                runCatching {
                    finishManual("群打卡触发失败：${t.message ?: t::class.java.simpleName}", failed = true)
                }.onFailure { diag("异常回执也失败：${it.javaClass.simpleName}：${it.message}") }
            }
        }
        return true
    }

    /**
     * 手动点击的后台处理。
     *
     * 顺序固定：正在执行判定 → 今日记录短路 → 登录判定 → 真正开跑。
     * 短路分支只读今日打卡记录与今日成功状态，目标群来自配置或上次落盘的目标，
     * **不读宿主群列表、不逐群判断**，所以“开始”之后能立刻跟出“已完成”。
     */
    private suspend fun handleManualClick(clickAt: Long) {
        // 自证日志：点击时刻、模式、是否在跑、通知开关，先记下来再判定。
        diag(
            "点击立即执行：时间=${clockTime(clickAt)}（$clickAt）running=$running " +
                "模式=${if (targetMode == TARGET_ALL) "全部已加入群" else "指定群号"} " +
                "通知开关=${if (notificationEnabled()) "开" else "关"}"
        )
        if (running) {
            val heldMs = System.currentTimeMillis() - runningSince
            if (heldMs in 0..STUCK_TIMEOUT_MS) {
                // 手动点击必须有反馈：即使“群打卡提示”开关被关，也弹这一次。
                finishManual("本次未执行：上一轮仍在执行中（已 ${heldMs / 1000} 秒）")
                diag("点击被挡：running=true held=${heldMs}ms")
                return
            }
            Log.w("群打卡：running 已持续 ${heldMs}ms 未复位，强制复位后重新执行")
            diag("running 超时未复位（${heldMs}ms），强制复位")
            running = false
        }
        // 同步通知形态：单条模式下这一步会撤掉上一轮遗留的结果通知。
        // 放在 running 判定之后，避免动到上一轮仍在执行的通知状态。
        notifyBegin()
        // 以今日打卡记录为准的快速判定：命中时直接给结束回执并返回。
        completedFromTodayLog()?.let { reason ->
            diag("点击短路命中：$reason")
            finishManual(reason)
            return
        }
        if (!QQInterfaces.isLogin) {
            finishManual("本次未执行：QQ 未登录", failed = true)
            diag("点击失败：QQ 未登录")
            return
        }
        diag("手动触发")
        startRun(auto = false, manual = true)
    }

    /**
     * 手动执行的结束回执：写 lastResult、弹应用内提示、发/改结果通知，
     * 并把本次结论与通知投递结果落到「今日打卡记录」页。
     *
     * 所有提前返回分支（今日已完成、目标群为空、正在执行中、未登录、解析/执行异常）都必须经过这里。
     */
    private fun finishManual(reason: String, failed: Boolean = false) {
        // 自证日志：先证明走到了这里，再记录提示与通知的结果。
        diag("finishManual 进入：failed=$failed 通知开关=${if (notificationEnabled()) "开" else "关"} 原因=$reason")
        // 每一步都单独兜住：任何一步失败（格式化、提示、落库）都不允许阻断结束通知。
        // r746 的事故就是 timeLabel 抛异常，异常发生在通知之前，结果通知一条都没发出去。
        lastResult = runCatching { "${timeLabel()} $reason" }.getOrDefault(reason)
        val toastFailure = runCatching { toastResult(reason, manual = true) }.exceptionOrNull()
        diag("finishManual 应用内提示：${if (toastFailure == null) "已调用" else "失败（${toastFailure.message}）"}")
        val notifyFailure = runCatching {
            if (failed) notifyResult(reason, failed = true) else notifySkipped(reason)
        }.exceptionOrNull()
        diag("finishManual 通知：${if (notifyFailure == null) "已提交" else "失败（${notifyFailure.message}）"}")
        runCatching { recordRun(reason) }
            .onFailure { diag("finishManual 记录失败：${it.javaClass.simpleName}：${it.message}") }
        diag("finishManual 退出：投递=${CheckInNotifier.lastDelivery}")
    }

    /**
     * 把最近一次执行记录写到隐藏配置里，供「今日打卡记录」页直接展示，
     * 用户不取日志文件也能看到时间、结论和通知投递结果。
     */
    private fun recordRun(reason: String) {
        val at = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val delivery = if (notificationEnabled()) {
            CheckInNotifier.lastDelivery.ifBlank { "未记录（本轮没有发通知）" }
        } else {
            "通知开关关闭，本轮未发通知"
        }
        lastRunAt = at
        lastRunResult = reason
        lastRunNotify = delivery
        diag("最近一次执行：$at · $reason · 通知 $delivery · 日志 ${CheckInDiag.pathText()}")
    }

    override fun getSettingDesc(key: String): String? {
        val log = runCatching { GroupCheckInLog.today() }.getOrNull()
        val todayLine = "今日已打卡 ${log?.records?.size ?: 0} 个群" +
                if (log != null && log.records.isNotEmpty()) {
                    "：" + log.records.take(5).joinToString("、") { it.name.ifBlank { it.uin } }
                } else {
                    ""
                }
        val parts = mutableListOf(desc, todayLine)
        if (lastResult.isNotBlank()) parts += "上次执行：$lastResult"
        return parts.joinToString("\n")
    }

    private fun checkSchedule() {
        // 跨天清空监控面板：30 秒一次的 tick 里发现日期变了就把旧记录删掉。
        runCatching { GroupCheckInLog.ensureToday() }
        if (!isEnabled()) return
        if (running || !QQInterfaces.isLogin) return

        when (trigger) {
            TRIGGER_DAILY -> {
                val target = parseTime() ?: run {
                    Log.e("每日打卡时间格式无效：$time")
                    return
                }
                if (LocalTime.now().isBefore(target)) return
                if (attemptDay == today()) return
                startRun(auto = true)
            }

            TRIGGER_STARTUP -> {
                if (!startupPending) return
                startupPending = false
                startRun(auto = true)
            }
        }
    }

    private fun startRun(auto: Boolean, manual: Boolean = false) {
        if (running) {
            // 防御性早退：手动点击也必须给结束回执，不能静默。
            if (manual) finishManual("本次未执行：上一轮仍在执行中")
            return
        }
        running = true
        runningSince = System.currentTimeMillis()
        // 自动触发（定时/启动）不经过点击入口，这里补一次通知形态同步；
        // 手动路径已经同步过，重复调用只会再清一次空列表。
        notifyBegin()

        ModuleScope.launchIO(TAG) {
            try {
                val targets = resolveTargets()
                if (targets.isEmpty()) {
                    val reason = if (targetMode == TARGET_ALL) {
                        "本次未执行：未读取到已加入群，且未选择群号"
                    } else {
                        "本次未执行：未选择群号"
                    }
                    if (manual) {
                        finishManual(reason)
                    } else {
                        lastResult = "${timeLabel()} $reason"
                        recordRun(reason)
                        diag("自动执行跳过：$reason")
                    }
                    if (auto && trigger == TRIGGER_DAILY) attemptDay = today()
                    return@launchIO
                }

                // 落盘本次目标群：下次点击先用它做短路判定，不必再读宿主群列表。
                GroupCheckInLog.recordTargets(targetMode, targets)

                // 开跑前先整体判断今日完成状态：只要全部已完成，立刻给结论，不进任何群。
                val done = completedToday(targets)
                val remaining = targets.filter { it !in done }
                if (remaining.isEmpty()) {
                    val text = "今天已全部打卡完成，无需重复（共 ${targets.size} 个群）"
                    if (manual) {
                        finishManual(text)
                    } else {
                        lastResult = "${timeLabel()} $text"
                        notifySkipped(text)
                        recordRun(text)
                        diag("结束：$text")
                    }
                    if (auto && trigger == TRIGGER_DAILY) attemptDay = today()
                    return@launchIO
                }
                if (manual) {
                    // “开始执行”已在点击时立即提示，这里不再重复，避免两条 toast 互相覆盖。
                    Log.i("手动执行：目标 ${targets.size} 个，已完成 ${done.size} 个")
                }
                diag("目标 ${targets.size} 个，已完成 ${done.size} 个，剩余 ${remaining.size} 个")
                notifyProgress("正在执行 ${remaining.size} 个群（跳过已完成 ${done.size} 个）…")

                val summary = try {
                    // 把本次判定好的完成集合一并传下去：执行循环里再判一次，
                    // 最终“成功/失败/跳过”三个数才对得上。
                    execute(auto, targets, done)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    Log.i("群打卡本轮被取消，不提示完成")
                    cancelNotification()
                    throw c
                } catch (t: Throwable) {
                    Log.e("群打卡执行异常", t)
                    null
                }

                if (summary == null) {
                    val text = "群打卡未完成：执行异常"
                    cancelNotification()
                    if (manual) {
                        finishManual(text, failed = true)
                    } else {
                        lastResult = "${timeLabel()} $text"
                        notifyResult(text, failed = true)
                        recordRun(text)
                    }
                    return@launchIO
                }

                lastResult = "${timeLabel()} ${summary.describe()}"
                Log.i("群打卡结束：${summary.describe()}")
                diag("结束：${summary.describe()}")

                // 只有整轮真正跑完才提示；未配置群号、执行异常等中断情况不弹“完成”。
                if (!summary.completed) {
                    if (manual) {
                        finishManual("群打卡未完成：${summary.describe()}", failed = true)
                    } else {
                        recordRun("群打卡未完成：${summary.describe()}")
                    }
                    return@launchIO
                }

                toastResult(summary.toastText(), manual)
                notifyResult(summary.toastText(), failed = summary.failed > 0)
                recordRun(summary.toastText())
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelNotification()
                throw c
            } catch (t: Throwable) {
                Log.e("群打卡执行异常", t)
                val text = "群打卡未完成：执行异常（${t.message ?: t::class.java.simpleName}）"
                cancelNotification()
                if (manual) {
                    finishManual(text, failed = true)
                } else {
                    lastResult = "${timeLabel()} $text"
                    notifyResult(text, failed = true)
                }
            } finally {
                // 任何分支都要复位，避免一次异常把后续点击永久挡在“正在执行中”。
                running = false
                runningSince = 0L
            }
        }
    }

    private fun resolveTargets(): List<String> {
        val configured = parseGroupNumbers(groups)
        return if (targetMode == TARGET_ALL) {
            runCatching { GroupSignInService.joinedGroupNumbers() }
                .getOrDefault(emptyList())
                .ifEmpty { configured }
        } else {
            configured
        }
    }

    /** 今天已经成功打卡、且仍在本次目标里的群。 */
    private fun completedToday(targets: List<String>): Set<String> =
        targets.filter { it in todaySuccessSet() }.toSet()

    /**
     * 手动点击的快速判定：只读今日打卡记录（`group_check_in_today.json`）与今日成功状态。
     *
     * 目标群来源分两种：
     * - 「指定群号」：直接取当前勾选，用户改了勾选立刻生效；
     * - 「全部已加入群」：当前群列表只在宿主里，此处刻意不读，改用上次执行落盘的目标群，
     *   但要求上次也是同一模式，否则目标集合不可比，返回 null 走完整流程。
     *
     * 完成判定必须与面板、执行流程同口径（`todaySuccessSet()` = success_state ∪ 记录文件）：
     * 只读文件会漏掉旧版本只写了 success_state 的群，表现就是“面板显示全部完成，点执行却不短路”。
     */
    private fun completedFromTodayLog(): String? {
        val log = runCatching { GroupCheckInLog.today() }.getOrNull() ?: return null
        val targets = if (targetMode == TARGET_LIST) {
            runCatching { parseGroupNumbers(groups) }.getOrDefault(emptyList())
        } else if (log.targetMode == targetMode) {
            log.targets
        } else {
            emptyList()
        }
        if (targets.isEmpty()) {
            diag("短路未命中：目标群为空（当前模式=$targetMode，上次模式=${log.targetMode}）")
            return null
        }
        val done = todaySuccessSet(log)
        val missing = targets.filter { it !in done }
        if (missing.isEmpty()) {
            diag("短路命中：目标 ${targets.size} 个，全部已完成（success_state/记录文件均计入）")
            return "今天已全部打卡完成，无需重复（共 ${targets.size} 个群）"
        }
        // 留一条可区分原因的日志：群号对不上 / 文件与配置不同步，都会在这里体现。
        val stateDay = successState.substringBefore('|', "")
        val stateCount = successState.substringAfter('|', "").split(',').count { it.isNotBlank() }
        diag(
            "短路未命中：目标 ${targets.size} 个，缺 ${missing.size} 个" +
                "（${missing.take(3).joinToString(",")}）；" +
                "success_state 日期=$stateDay 共 $stateCount 条，记录文件 ${log.records.size} 条"
        )
        return null
    }

    /**
     * 今日成功集合：同时看 [successState] 与落盘的今日记录。
     *
     * 两处都读是为了容错：配置里的成功状态被清掉时，记录文件仍是真实执行过打卡的凭据；
     * 反之记录文件跨天被删时，成功状态还能兜底。
     */
    private fun todaySuccessSet(log: GroupCheckInDayLog? = null): Set<String> {
        val day = today()
        val result = mutableSetOf<String>()
        if (successState.startsWith("$day|")) {
            result += successState.removePrefix("$day|")
                .split(',')
                .filter { it.isNotBlank() }
        }
        (log ?: runCatching { GroupCheckInLog.today() }.getOrNull())
            ?.records?.forEach { result += it.uin }
        return result
    }

    /**
     * 「今日打卡记录」面板的「删除今日记录」。
     *
     * 只清今日进度：记录文件 + 配置里的今日成功状态（两者共同决定“今天是否已打完”）。
     * 清掉后再点执行会重新打卡；群列表、打卡方式、触发时间等配置不受影响。
     */
    internal fun clearTodayProgress(): Boolean {
        val cleared = runCatching { GroupCheckInLog.clearToday() }.getOrDefault(false)
        successState = "${today()}|"
        diag("手动清空今日记录（文件删除=${if (cleared) "成功" else "无文件或失败"}）")
        return cleared
    }

    // ── 监控面板数据 ──────────────────────────────────────────────────────────

    internal data class MonitorTarget(val uin: String, val name: String)

    internal data class MonitorSnapshot(
        val targetAll: Boolean,
        val targets: List<MonitorTarget>,
        val completed: Set<String>,
        /** 读不到群列表/还没勾选等口径问题，界面原样展示。 */
        val error: String = "",
        /** 最近一次执行记录：时间、结论、通知投递结果（没有执行过时为空）。 */
        val lastRunAt: String = "",
        val lastRunResult: String = "",
        val lastRunNotify: String = "",
        /** 当前生效的模块版本与日志实际路径，用来确认跑的是哪一份载荷。 */
        val moduleVersion: String = "",
        val logPath: String = "",
        val logError: String = "",
    )

    /**
     * 给「今日打卡记录」面板用的真实口径：
     * 分母 = 当前勾选要打卡的群（指定群号用配置，全部已加入群用宿主群列表），
     * 分子 = 其中今天已经成功打卡的群。
     */
    internal fun monitorSnapshot(): MonitorSnapshot {
        val configured = runCatching { parseGroupNumbers(groups) }.getOrDefault(emptyList())
        val targetAll = targetMode == TARGET_ALL
        val joined = runCatching { GroupSignInService.joinedGroups() }.getOrDefault(emptyList())
        val names = joined.associate { it.uin to it.name }
        val numbers: List<String>
        var error = ""
        if (targetAll) {
            val joinedNumbers = joined.map { it.uin }
            if (joinedNumbers.isEmpty()) {
                numbers = configured
                error = if (configured.isEmpty()) {
                    "未读取到已加入的群（宿主群列表接口暂时不可用）"
                } else {
                    "未读取到已加入的群，暂按群号列表里的 ${configured.size} 个群统计"
                }
            } else {
                numbers = joinedNumbers
            }
        } else {
            numbers = configured
            if (configured.isEmpty()) error = "还没有勾选要打卡的群"
        }
        return MonitorSnapshot(
            targetAll = targetAll,
            targets = numbers.distinct().map { MonitorTarget(it, names[it].orEmpty()) },
            completed = todaySuccessSet(),
            error = error,
            lastRunAt = lastRunAt,
            lastRunResult = lastRunResult,
            lastRunNotify = lastRunNotify,
            moduleVersion = TCQTBuild.VER_NAME,
            logPath = CheckInDiag.pathText(),
            logError = CheckInDiag.lastError,
        )
    }

    /**
     * 真正执行打卡的循环。
     *
     * [targets] 是本次目标全景（用于统计“跳过”），[preDone] 是进入执行前已经完成的群：
     * 已完成的一律跳过，不产生任何请求，并在循环里再判一次（防止中途被其它触发写成完成）。
     */
    private suspend fun execute(auto: Boolean, targets: List<String>, preDone: Set<String>): RunSummary {
        if (auto) attemptDay = today()

        val day = today()
        val done = preDone.toMutableSet()
        if (!successState.startsWith("$day|")) successState = "$day|"

        var ok = 0
        var failed = 0
        var skipped = 0
        val failedGroups = mutableListOf<String>()
        // 面板要显示群名；取不到就只显示群号。
        val groupNames = runCatching {
            GroupSignInService.joinedGroups().associate { it.uin to it.name }
        }.getOrDefault(emptyMap())

        targets.forEachIndexed { index, group ->
            if (group in done) {
                skipped++
                // 今天已经成功的群，把之前留下的失败记录清掉，面板才不会误报。
                GroupCheckInLog.clearFailure(group)
                refreshProgress(ok, failed, skipped, targets.size)
                return@forEachIndexed
            }

            val result = runWithRetry(group)
            if (result.ok) {
                ok++
                done += group
                successState = "$day|" + done.joinToString(",")
                GroupCheckInLog.record(group, groupNames[group].orEmpty())
            } else {
                failed++
                failedGroups += group
                GroupCheckInLog.recordFailure(group, groupNames[group].orEmpty(), result.reason)
            }
            refreshProgress(ok, failed, skipped, targets.size)

            // 后面已经没有待执行的群时不再空等一个群间隔。
            if (targets.drop(index + 1).any { it !in done }) {
                delay(interval * 1000L)
            }
        }

        return RunSummary(
            ok = ok,
            failed = failed,
            skipped = skipped,
            failedGroups = resolveGroupNames(failedGroups),
            completed = true,
        )
    }

    /** 失败提示里优先显示群名，取不到名字时退回群号。 */
    private fun resolveGroupNames(uins: List<String>): List<String> {
        if (uins.isEmpty()) return emptyList()
        val names = runCatching {
            GroupSignInService.joinedGroups().associate { it.uin to it.name }
        }.getOrElse { emptyMap() }
        return uins.map { uin -> names[uin]?.takeIf { it.isNotBlank() && it != uin } ?: uin }
    }

    private data class AttemptResult(val ok: Boolean, val reason: String = "")

    /** 配置里的“重试 N 次”是失败后的额外次数，所以总尝试次数是 N + 1，最多 4 次。 */
    private suspend fun runWithRetry(group: String): AttemptResult {
        val attempts = (retry + 1).coerceIn(1, 4)
        var reason = ""
        repeat(attempts) { index ->
            val outcome = runCatching {
                if (mode == MODE_MESSAGE) {
                    GroupSignInService.sendGroupMessage(group, command)
                } else {
                    GroupSignInService.signIn(group)
                }
            }
            if (outcome.getOrDefault(false)) return AttemptResult(true)
            reason = outcome.exceptionOrNull()
                ?.let { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName }
                ?: if (mode == MODE_MESSAGE) "群消息发送失败" else "签到接口返回失败"
            Log.e("群 $group 打卡失败（第 ${index + 1}/$attempts 次）：$reason")
            if (index < attempts - 1) delay(RETRY_DELAY_MS)
        }
        return AttemptResult(
            ok = false,
            reason = if (attempts > 1) "$reason（已尝试 $attempts 次）" else reason,
        )
    }

    // ── 提示通道：两个独立开关，语义对齐 XAutoDaily ─────────────────────────────
    //
    // 「群打卡提示」= QQ 内置轻提示（前台可见），默认开；
    // 「群打卡通知」= 系统通知（后台可达），默认关。两者互不影响，可同时开启。

    private fun toastEnabled(): Boolean = runCatching {
        TCQTSetting.getValue<Boolean>(GroupCheckInToast.key) ?: true
    }.getOrDefault(true)

    private fun notificationEnabled(): Boolean = runCatching {
        TCQTSetting.getValue<Boolean>(GroupCheckInNotification.key) ?: true
    }.getOrDefault(true)

    /** 单条通知模式：整轮只留一条，开始→逐群刷新→原地改成结果。 */
    private fun singleNotificationEnabled(): Boolean = runCatching {
        TCQTSetting.getValue<Boolean>(GroupCheckInSingleNotification.key) ?: true
    }.getOrDefault(true)

    private fun toastInfo(message: String) {
        if (toastEnabled()) QQToastCompat.info(message)
    }

    /**
     * 结束提示。手动点击时固定用 TYPE_INFO 且忽略开关：
     * 开始回执就是 TYPE_INFO，部分宿主版本对 success/error 类型会静默不显示，
     * 统一成同一类型后，“点一次只有开始一条”的情况不会再出现。
     */
    private fun toastResult(message: String, manual: Boolean) {
        if (manual) {
            QQToastCompat.info(message)
        } else {
            toastInfo(message)
        }
    }

    /**
     * 每轮开始前同步通知形态。
     *
     * 单条模式下会顺手撤掉上一轮遗留的结果通知，保证通知栏里最多只剩本轮这一条。
     */
    private fun notifyBegin() {
        lastProgressRefreshAt = 0L
        if (notificationEnabled()) CheckInNotifier.begin(singleNotificationEnabled())
    }

    private fun notifyProgress(text: String) {
        if (notificationEnabled()) CheckInNotifier.progress(text)
    }

    /**
     * 单条模式的逐群进度刷新：正文形如「已完成 12/56，跳过 3，失败 0」。
     *
     * 只在该模式且通知开关打开时生效，并按 [PROGRESS_REFRESH_GAP_MS] 节流；
     * 旧模式（开始+结果）保持原行为，不刷新进度。
     */
    private fun refreshProgress(ok: Int, failed: Int, skipped: Int, total: Int) {
        if (!notificationEnabled() || !singleNotificationEnabled()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastProgressRefreshAt < PROGRESS_REFRESH_GAP_MS) return
        lastProgressRefreshAt = now
        val handled = ok + failed + skipped
        CheckInNotifier.updateProgress("已完成 $handled/$total，跳过 $skipped，失败 $failed")
    }

    private fun notifySkipped(text: String) {
        if (notificationEnabled()) CheckInNotifier.skipped(text)
    }

    private fun notifyResult(text: String, failed: Boolean) {
        if (notificationEnabled()) CheckInNotifier.result(text, failed)
    }

    private fun cancelNotification() {
        if (notificationEnabled()) CheckInNotifier.cancel()
    }

    /**
     * 自动落盘的执行日志，交给 [CheckInDiag] 统一处理：
     * 主位置 `<moduleDataPath>/group_check_in_diag.log`（宿主私有 files，必然可写），
     * 并尽力镜像到 `/sdcard/Android/data/<宿主包名>/files/<模块名>/` 方便直接取出。
     */
    private fun diag(text: String) = CheckInDiag.log(text)

    private fun isEnabled(): Boolean = TCQTSetting.getValue<Boolean>(key) ?: false

    private fun parseTime(): LocalTime? = runCatching {
        val values = time.trim().split(":", "：")
        LocalTime.of(values[0].trim().toInt(), values[1].trim().toInt())
    }.getOrNull()

    private fun today(): String = LocalDate.now().toString()

    /**
     * 结果前缀时间。必须用 [LocalDateTime]：`MM-dd` 含月/日字段，
     * 用 `LocalTime` 会抛 `Unsupported field: MonthOfYear`（LocalTime 只有时分秒）。
     */
    private fun timeLabel(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    /** 日志里的毫秒级时刻，用来核对“点击 → 改写”的间隔。 */
    private fun clockTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))

    private data class RunSummary(
        val ok: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val failedGroups: List<String> = emptyList(),
        val completed: Boolean = false,
        val note: String? = null,
    ) {
        fun describe(): String {
            if (note != null && !completed) return note
            val base = "成功 $ok，失败 $failed" + if (skipped > 0) "，跳过 $skipped" else ""
            val failedText = if (failedGroups.isEmpty()) "" else "（失败群：${failedGroups.joinToString("、")}）"
            return base + failedText + if (note == null) "" else "（$note）"
        }

        /** 一轮结束后的提示文案：全部成功与部分失败用不同措辞。 */
        fun toastText(): String {
            if (ok == 0 && failed == 0) {
                return "群打卡完成：本次无需打卡（跳过已完成的 $skipped 个群）"
            }
            val parts = buildList {
                if (ok > 0) add("成功 $ok 个")
                if (failed > 0) add("失败 $failed 个")
                if (skipped > 0) add("跳过 $skipped 个")
            }.joinToString("，")
            if (failed == 0) return "群打卡完成：$parts"
            val shown = failedGroups.take(3).joinToString("、")
            val more = if (failedGroups.size > 3) " 等 ${failedGroups.size} 个" else ""
            return "群打卡未完成：$parts（失败群：$shown$more）"
        }
    }
}
