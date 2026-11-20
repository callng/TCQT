package com.owo233.tcqt.host.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQToastCompat
import com.owo233.tcqt.core.log.Log

/**
 * 群打卡通知（对齐 XAutoDaily 的「启用签到通知」）。
 *
 * 两种收尾路径，都带自检与回退，任何一条都保证通知栏里能看到结论：
 * - **短路命中**（今日已完成等）：本轮从未发过「开始」，直接发**一条**结果通知，
 *   不再产生“开始不更新”的残留；
 * - **正常执行**：结束时先撤销「开始」那条（[PROGRESS_ID]），再发新结果通知；
 *   若新结果没落地（ROM 丢弃 / 渠道被关），退回把 [PROGRESS_ID] 原地改写成结果。
 *
 * 每一步都会把渠道重要性、notify 结果、`activeNotifications` 快照写进 [CheckInDiag]；
 * 最终投递结论保存在 [lastDelivery]，由「今日打卡记录」页面直接展示，不依赖外部日志文件。
 * 与 QQToast 是两条独立通道。
 *
 * 取 Context 只走 [HookEnv.hostAppContext]（由宿主 `Application` 注入），
 * 不依赖任何 Xposed/Zygisk 专有 API，因此 LSPosed 与 Zygisk 两种注入模式行为一致。
 */
internal object CheckInNotifier {

    /**
     * v2 渠道：旧渠道 [LEGACY_CHANNEL_ID] 曾被创建为 `IMPORTANCE_LOW` 且关闭了声音/振动，
     * 用户即使没关通知也看不到明显提醒。渠道一旦创建就不能由应用提高等级，
     * 因此换新 id 重新按 `IMPORTANCE_DEFAULT` 创建（新渠道会正常提醒）。
     */
    private const val CHANNEL_ID = "tcqt_group_check_in_v2"
    private const val LEGACY_CHANNEL_ID = "tcqt_group_check_in"
    private const val CHANNEL_NAME = "群打卡提醒"

    /**
     * 结果专用渠道：与执行中的进度渠道分开。
     *
     * 进度通知是常驻、低打扰的；用户/ROM 往往会把这类渠道调低甚至静音，而渠道一旦被调低，
     * 应用无法再提高。结果通知另用新渠道按 `IMPORTANCE_HIGH` 创建，能在通知栏弹出横幅，
     * 不受进度渠道设置影响。旧渠道上的历史设置不再拖累结果提醒。
     */
    private const val RESULT_CHANNEL_ID = "tcqt_group_check_in_result_v1"
    private const val RESULT_CHANNEL_NAME = "群打卡结果"

    /** 执行中与结果用两个 id 段：结果每次换新 id，确保同一轮多次执行都会重新提醒。 */
    private const val PROGRESS_ID = 20001
    private const val RESULT_ID = 20002

    /** 结果通知 id 在 [RESULT_ID] 起的这一段里循环，最多同时保留一条。 */
    private const val RESULT_ID_RANGE = 900

    private val resultIdLock = Any()
    private var lastResultId = RESULT_ID

    /** 单条通知模式：整轮只用 [PROGRESS_ID] 这一条，结束时原地改成结果文案。 */
    @Volatile
    private var singleMode = false

    /**
     * 已经 post 出去、还没被清理的结果通知 id。
     *
     * 旧模式每轮换新 id 且从不清理，通知栏会累积；切到单条模式时按这张表把它们一起撤掉，
     * 保证通知栏里最多只剩本轮那一条。
     */
    private val postedResultIds = ArrayDeque<Int>()

    /** 本轮是否真的发出过「开始」通知（仅用于选择收尾策略，不用于判定成败）。 */
    @Volatile
    private var startPosted = false

    /**
     * 每轮开始时调用（手动点击与自动触发都要走）。
     *
     * 单条模式下先把历史结果通知全部撤掉，避免上一轮的结果和本轮的开始并存。
     */
    fun begin(single: Boolean) {
        singleMode = single
        startPosted = false
        if (!single) return
        val stale = synchronized(resultIdLock) {
            val copy = postedResultIds.toList()
            postedResultIds.clear()
            copy
        }
        if (stale.isEmpty()) return
        runCatching {
            val manager = HookEnv.hostAppContext.getSystemService(NotificationManager::class.java)
            stale.forEach { manager?.cancel(it) }
            diag("单条模式：撤销上一轮结果通知 ${stale.size} 条（${stale.joinToString(",")}）")
        }
    }

    /**
     * 最近一次投递结论，例如「结果通知已显示（id=20003）」「已退回改写开始通知」。
     * 由「今日打卡记录」页面直接展示，用户不必去翻日志文件。
     */
    @Volatile
    var lastDelivery: String = ""
        private set

    /** 执行中：常驻、不响铃、不可划掉。 */
    fun progress(text: String) {
        val context = runCatching { HookEnv.hostAppContext }.getOrNull()
        val manager = context?.getSystemService(NotificationManager::class.java)
        if (context == null || manager == null) {
            startPosted = false
            lastDelivery = "开始通知未发（${if (context == null) "Context 未就绪" else "通知服务不可用"}）"
            diag("开始通知跳过：${if (context == null) "Context 未就绪" else "通知服务不可用"}（$text）")
            toastFallback("群打卡", text)
            return
        }
        val failure = runCatching {
            ensureChannel(manager, resultChannel = false)
            removeLegacyChannel(manager)
            manager.notify(
                PROGRESS_ID,
                buildNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    title = "群打卡",
                    text = text,
                    ongoing = true,
                    onlyAlertOnce = true,
                ),
            )
        }.exceptionOrNull()?.message
        val alive = runCatching { manager.activeNotifications.any { it.id == PROGRESS_ID } }.getOrNull()
        startPosted = failure == null && alive == true
        diag(
            "开始通知：id=$PROGRESS_ID 渠道=$CHANNEL_ID " +
                "渠道重要性=${importanceOf(manager, CHANNEL_ID)} 通知总开关=${notificationsEnabled(context)} " +
                "notify=${failure ?: "ok"} 通知栏含该条=$alive"
        )
    }

    /**
     * 单条模式下逐群原地刷新正文（节流由调用方负责）。
     *
     * 只做一次 `notify`：不查 `activeNotifications`、不写诊断日志。
     * 群多时这是每 1~2 秒一次的调用，任何额外开销都会被放大。
     */
    fun updateProgress(text: String) {
        val (context, manager) = contextAndManager() ?: return
        val failure = runCatching {
            ensureChannel(manager, resultChannel = false)
            manager.notify(
                PROGRESS_ID,
                buildNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    title = "群打卡",
                    text = text,
                    ongoing = true,
                    onlyAlertOnce = true,
                ),
            )
        }.exceptionOrNull()
        if (failure != null) diag("进度刷新失败：${failure.message}（$text）")
    }

    /** 执行结束：结果通知，点击后回到 QQ 主界面。 */
    fun result(text: String, failed: Boolean) {
        val title = if (failed) "群打卡完成（有失败）" else "群打卡完成"
        if (singleMode) {
            finishSingleMode(title, text)
            return
        }
        if (startPosted) finishAfterStart(title, text) else finishSingle(title, text)
    }

    /** 本次未执行（今日已完成 / 没有目标群 / 正在执行中等）：本轮没有开始通知，只发一条结果。 */
    fun skipped(text: String) {
        if (singleMode) {
            finishSingleMode("群打卡未执行", text)
            return
        }
        finishSingle("群打卡未执行", text)
    }

    /** 清理通知（执行异常/被取消时调用，避免通知一直挂着）。 */
    fun cancel() {
        startPosted = false
        synchronized(resultIdLock) { postedResultIds.clear() }
        runCatching {
            val context = HookEnv.hostAppContext
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(PROGRESS_ID)
            manager?.cancel(lastResultId)
            manager?.cancel(RESULT_ID)
        }.onFailure { Log.w("群打卡通知：清理失败（${it.message}）") }
    }

    /**
     * 单条模式的收尾：把本轮「开始」那条原地改成结果，不再发第二条。
     *
     * 整轮只有 [PROGRESS_ID] 一条通知：开始 → 逐群刷新 → 结果，全在同一 id 上完成。
     */
    private fun finishSingleMode(title: String, text: String) {
        val (context, manager) = contextAndManager() ?: run {
            lastDelivery = "通知未发（$noContextReason）"
            diag("单条模式结果跳过：$noContextReason，改用应用内提示（$text）")
            toastFallback(title, text)
            return
        }
        startPosted = false
        val rewritten = rewriteProgress(context, manager, title, text)
        diag("单条模式结果：原地改写 id=$PROGRESS_ID 落地=$rewritten 文案=$text")
        if (rewritten) {
            lastDelivery = "结果已写回同一条通知（id=$PROGRESS_ID）"
            return
        }
        lastDelivery = "单条通知未落地（进度渠道被系统丢弃）"
        toastFallback(title, text)
    }

    /** 记住本轮用掉的结果通知 id，供下一轮（切到单条模式时）清理。 */
    private fun rememberResultId(id: Int) {
        synchronized(resultIdLock) {
            postedResultIds.addLast(id)
            while (postedResultIds.size > RESULT_ID_RANGE) postedResultIds.removeFirst()
        }
    }

    /**
     * 没有「开始」通知时（短路命中）：直接发**一条**结果通知。
     *
     * 优先结果渠道；若它在通知栏里查不到，再退回进度通知 id 原地写一条，
     * 保证最终只有一条结论通知，不会留下“开始不更新”的残留。
     */
    private fun finishSingle(title: String, text: String) {
        val (context, manager) = contextAndManager() ?: run {
            lastDelivery = "通知未发（$noContextReason）"
            diag("单条结果通知跳过：$noContextReason，改用应用内提示（$text）")
            toastFallback(title, text)
            return
        }

        val id = nextResultId()
        val posted = postResult(context, manager, id, title, text)
        diag(
            "单条结果通知：id=$id 渠道=$RESULT_CHANNEL_ID " +
                "渠道重要性=${importanceOf(manager, RESULT_CHANNEL_ID)} 通知总开关=${notificationsEnabled(context)} " +
                "落地=$posted 文案=$text"
        )
        if (posted) {
            lastDelivery = "结果通知已显示（id=$id，单条）"
            rememberResultId(id)
            return
        }

        // 结果渠道没落地：退回进度 id 原地写一条（渠道不同，可能反而可用）。
        val rewritten = rewriteProgress(context, manager, title, text)
        diag("单条结果未落地，退回改写 id=$PROGRESS_ID：落地=$rewritten")
        if (rewritten) {
            lastDelivery = "结果渠道未落地，已退回进度通知显示（id=$PROGRESS_ID）"
            return
        }
        lastDelivery = "通知全部未落地（结果渠道与进度渠道都被系统丢弃）"
        toastFallback(title, text)
    }

    /**
     * 正常执行结束：先撤销「开始」那条，再发新结果通知。
     *
     * 新结果没落地时，退回把 [PROGRESS_ID] 原地改写成结果；
     * 这样既不会出现“开始那条一直不变”，也不会两条并存。
     */
    private fun finishAfterStart(title: String, text: String) {
        val (context, manager) = contextAndManager() ?: run {
            lastDelivery = "通知未发（$noContextReason）"
            diag("结束通知跳过：$noContextReason，改用应用内提示（$text）")
            toastFallback(title, text)
            return
        }

        // 1) 先撤掉常驻的开始通知，避免用户看到“开始”停留在通知栏。
        val cancelFailure = runCatching { manager.cancel(PROGRESS_ID) }.exceptionOrNull()?.message
        val stillThere = runCatching { manager.activeNotifications.any { it.id == PROGRESS_ID } }.getOrNull()
        startPosted = false
        diag("结束：撤销开始通知 id=$PROGRESS_ID cancel=${cancelFailure ?: "ok"} 撤销后仍在=$stillThere 文案=$text")

        // 2) 再发一条全新 id 的结果通知。
        val id = nextResultId()
        val posted = postResult(context, manager, id, title, text)
        diag(
            "结果通知：id=$id 渠道=$RESULT_CHANNEL_ID " +
                "渠道重要性=${importanceOf(manager, RESULT_CHANNEL_ID)} 通知总开关=${notificationsEnabled(context)} " +
                "落地=$posted 文案=$text"
        )
        if (posted) {
            lastDelivery = "结果通知已显示（id=$id）"
            rememberResultId(id)
            return
        }

        // 3) 没落地：退回把开始那条的 id 原地改写成结果。
        val rewritten = rewriteProgress(context, manager, title, text)
        diag("结果通知未落地，退回改写 id=$PROGRESS_ID：落地=$rewritten")
        if (rewritten) {
            lastDelivery = "结果渠道未落地，已退回改写开始通知（id=$PROGRESS_ID）"
            return
        }
        lastDelivery = "通知全部未落地（结果渠道与进度渠道都被系统丢弃）"
        toastFallback(title, text)
    }

    private var noContextReason: String = "Context 未就绪"

    private fun contextAndManager(): Pair<Context, NotificationManager>? {
        val context = runCatching { HookEnv.hostAppContext }.getOrNull()
        if (context == null) {
            noContextReason = "Context 未就绪"
            return null
        }
        val manager = runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
        if (manager == null) {
            noContextReason = "通知服务不可用"
            return null
        }
        return context to manager
    }

    /** 发结果通知并自检：`notify` 不抛异常且通知栏里查得到这条才算落地。 */
    private fun postResult(
        context: Context,
        manager: NotificationManager,
        id: Int,
        title: String,
        text: String,
    ): Boolean {
        ensureChannel(manager, resultChannel = true)
        val failure = runCatching {
            manager.notify(
                id,
                buildNotification(
                    context = context,
                    channelId = RESULT_CHANNEL_ID,
                    title = title,
                    text = text,
                    ongoing = false,
                    onlyAlertOnce = false,
                ),
            )
        }.exceptionOrNull()?.message
        if (failure != null) return false
        val alive = runCatching { manager.activeNotifications.any { it.id == id } }.getOrNull()
        return alive == true
    }

    /** 把 [PROGRESS_ID] 原地写成结果文案，并确认正文已更新。 */
    private fun rewriteProgress(
        context: Context,
        manager: NotificationManager,
        title: String,
        text: String,
    ): Boolean {
        ensureChannel(manager, resultChannel = false)
        val failure = runCatching {
            manager.notify(
                PROGRESS_ID,
                buildNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    title = title,
                    text = text,
                    ongoing = false,
                    onlyAlertOnce = true,
                ),
            )
        }.exceptionOrNull()?.message
        if (failure != null) return false
        val active = runCatching { manager.activeNotifications.firstOrNull { it.id == PROGRESS_ID } }.getOrNull()
            ?: return false
        val activeText = runCatching {
            active.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        }.getOrNull()
        return activeText == null || activeText == text
    }

    private fun nextResultId(): Int = synchronized(resultIdLock) {
        lastResultId = if (lastResultId >= RESULT_ID + RESULT_ID_RANGE) RESULT_ID else lastResultId + 1
        lastResultId
    }

    private fun importanceOf(manager: NotificationManager, channelId: String): String =
        runCatching {
            val channel = manager.getNotificationChannel(channelId) ?: return@runCatching "渠道不存在"
            "${channel.importance}（${importanceLabel(channel.importance)}）"
        }.getOrDefault("查询失败")

    private fun importanceLabel(importance: Int): String = when (importance) {
        NotificationManager.IMPORTANCE_NONE -> "已关闭"
        NotificationManager.IMPORTANCE_MIN -> "最低"
        NotificationManager.IMPORTANCE_LOW -> "低（静音）"
        NotificationManager.IMPORTANCE_DEFAULT -> "默认"
        NotificationManager.IMPORTANCE_HIGH -> "高（横幅）"
        else -> "未知"
    }

    private fun notificationsEnabled(context: Context): String = runCatching {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "开" else "关"
    }.getOrDefault("查询失败")

    private fun buildNotification(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        ongoing: Boolean,
        onlyAlertOnce: Boolean,
    ): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openHost(context))
            .build()

    private fun toastFallback(title: String, text: String) {
        runCatching { QQToastCompat.info("$title：$text") }
            .onFailure { Log.w("群打卡通知：应用内提示也失败（${it.message}）") }
    }

    /** 自动落盘的通知诊断，和 [GroupCheckIn] 共用同一份日志（含外部镜像）。 */
    private fun diag(text: String) = CheckInDiag.log(text)

    private fun ensureChannel(manager: NotificationManager, resultChannel: Boolean) {
        val channelId = if (resultChannel) RESULT_CHANNEL_ID else CHANNEL_ID
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = if (resultChannel) {
            NotificationChannel(
                RESULT_CHANNEL_ID,
                RESULT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "群打卡结束时的执行结果（含已全部完成、未执行等情况）"
                enableVibration(false)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        } else {
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "群打卡执行进度"
                enableVibration(false)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        }
        manager.createNotificationChannel(channel)
    }

    /** 旧的静音渠道留着会占列表；用户没自定义过就删掉。 */
    private fun removeLegacyChannel(manager: NotificationManager) {
        runCatching {
            val legacy = manager.getNotificationChannel(LEGACY_CHANNEL_ID) ?: return
            if (legacy.importance <= NotificationManager.IMPORTANCE_LOW) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
        }
    }

    /**
     * 点击通知回到宿主主界面。
     *
     * 不能指向 `com.owo233.tcqt.ui.settings.SettingActivity`：它是模块类，未声明在
     * 宿主 manifest 里，寄生 Activity 的替换只在宿主进程内 `startActivity` 时生效，
     * 系统侧发送的 PendingIntent 不经过该路径，静态就不可达。
     * 这里用 PackageManager 解析宿主自身的 MAIN/LAUNCHER，组件由宿主 manifest 声明，
     * 系统一定能启动；解析失败时退回隐式 LAUNCHER Intent（系统按包名解析）。
     * 两者都拿不到时返回 null（通知仍显示，仅无点击行为）。
     */
    private fun openHost(context: Context): PendingIntent? {
        val launcher = context.packageManager
            .getLaunchIntentForPackage(HookEnv.hostAppPackageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(HookEnv.hostAppPackageName)

        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            PendingIntent.getActivity(
                context,
                RESULT_ID,
                launcher,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.onFailure {
            Log.w("群打卡通知：创建点击跳转失败（${it.message}）")
        }.getOrNull()
    }
}
