package com.owo233.tcqt.host.service

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.os.SystemClock
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.channels.FileLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 好友历史落盘结构版本：2 起每人带明确状态、每条事件带类型与时间区间、快照带未读标记。 */
internal const val CURRENT_VERSION = 2

/**
 * 好友历史：按账号保存在模块自身的配置目录，不写 `/data/adb/tcqt`（该目录为 root 所有，
 * 宿主进程无写权限），也不与官方 TCQT / QAuxiliary 的存储混用。
 *
 * 路径：`<moduleDataPath>/account/<uin>/friend_history.json`
 */
@Serializable
internal data class FriendHistoryRecord(
    /** 好友 QQ 号，字符串形式避免长整型在 JSON 里的精度歧义。 */
    val uin: String,
    val nick: String = "",
    val remark: String = "",
    /** 头像地址；宿主列表接口不直接给时按 QQ 号拼 qlogo 地址，保证基线里可持久化。 */
    val avatar: String = "",
    /** 最近一次确认仍在好友列表里的时间（秒）。 */
    val seenAt: Long = 0,
    /** 是否出现在上一轮完整好友列表同步中；false 表示尚无可靠基线。 */
    val present: Boolean = false,
    /** 最近一次判定删除的时间（秒），0 表示未删除。 */
    val deletedAt: Long = 0,
    /**
     * 每人一条的明确状态：0 未确认、1 在列、2 已删除（同步发现）、3 我主动删除。
     *
     * 0 同时是**迁移兼容值**：旧文件没有这个字段，读出来就是 0，此时由 [resolvedStatus]
     * 按 [present] / [deletedAt] 推导，保证老数据不会因为缺字段被显示成错状态。
     */
    val status: Int = STATUS_UNKNOWN,
) {
    /** 结合旧字段推导后的真实状态；界面与徽标一律用这个，不要直接读 [status]。 */
    val resolvedStatus: Int
        get() = when {
            status != STATUS_UNKNOWN -> status
            deletedAt > 0 -> STATUS_DELETED
            present -> STATUS_FRIEND
            else -> STATUS_UNKNOWN
        }

    /** 当前是否在好友列表里（决定历史记录行的徽标是「已恢复」还是「已删除」）。 */
    val inList: Boolean get() = resolvedStatus == STATUS_FRIEND

    companion object {
        const val STATUS_UNKNOWN = 0
        const val STATUS_FRIEND = 1
        const val STATUS_DELETED = 2
        const val STATUS_ACTIVE_DELETED = 3
    }
}

@Serializable
internal data class FriendHistoryEvent(
    val uin: String,
    val nick: String = "",
    val remark: String = "",
    /** 检测到删除/手动添加的时间（秒）。 */
    val detectedAt: Long = 0,
    /** 依据的最后在线时间（秒），用于“在约 X 前删除了你”。 */
    val seenAt: Long = 0,
    /**
     * `sync` 好友列表同步发现删除，`active` 自己删除好友，`manual` 手动添加，
     * `added` 宿主新增好友回调即时写入。
     */
    val source: String = SOURCE_SYNC,
    /**
     * 事件类型：0 由 [source] 推导、2 新增好友、3 删除好友（编号与 QAuxiliary 一致）。
     *
     * 0 是迁移兼容值：旧文件没有这个字段，读出来按 [resolvedType] 从 [source] 推导。
     */
    val type: Int = TYPE_UNKNOWN,
    /**
     * 检测窗口起点（秒）：上一次成功同步的时间。
     *
     * 列表上显示成「上次同步 ~ 本次」的区间，避免把删除假装成精确到秒的某个时刻。
     * 旧事件没有这个值（0），界面退化为只显示检测时间点。
     */
    val timeRangeBegin: Long = 0,
) {
    val displayName: String
        get() = remark.ifBlank { nick }.ifBlank { uin }

    /** 事件类型；旧数据按来源推导，保证迁移后旧事件也能正确分组与展示。 */
    val resolvedType: Int
        get() = when {
            type != TYPE_UNKNOWN -> type
            source == SOURCE_SYNC || source == SOURCE_ACTIVE -> TYPE_DELETE
            else -> TYPE_ADD
        }

    /** 检测窗口终点，沿用 [detectedAt]。 */
    val timeRangeEnd: Long get() = detectedAt

    companion object {
        const val SOURCE_SYNC = "sync"
        const val SOURCE_ACTIVE = "active"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_ADDED = "added"

        const val TYPE_UNKNOWN = 0
        const val TYPE_ADD = 2
        const val TYPE_DELETE = 3
    }
}

@Serializable
internal data class FriendHistorySnapshot(
    val version: Int = CURRENT_VERSION,
    val accountUin: String = "",
    /** 最近一次完整好友列表同步的服务器时间（秒），用于节流主动刷新。 */
    val lastSyncAt: Long = 0,
    /** 是否已经建立过基线；false 时首轮同步只建基线、不产生删除记录。 */
    val initialized: Boolean = false,
    /** 最近一次完整同步里实际解析到的好友数，用于界面自证“数据真的进来了”。 */
    val lastFriendCount: Int = 0,
    /** 最近一次主动刷新好友列表的时间（秒）与结果，用于界面显示检测是否在工作。 */
    val lastAttemptAt: Long = 0,
    val lastAttemptResult: String = "",
    /** 最近一次“登录读取人数并比对”的时间与结果；人数没变时不会更新明细。 */
    val lastCountCheckAt: Long = 0,
    val lastCountCheckResult: String = "",
    /** 未读标记：最近一次未读事件的检测时间（秒），0 表示没有未读。 */
    val unreadSince: Long = 0,
    val friends: Map<String, FriendHistoryRecord> = emptyMap(),
    val events: List<FriendHistoryEvent> = emptyList(),
    /** 排除名单：同一份名单同时作用于“记录”和“通知”。 */
    val exclusions: List<String> = emptyList(),
) {
    val exclusionSet: Set<String> get() = exclusions.toSet()

    /** 是否有未读的新变动（入口红点）。 */
    val hasUnread: Boolean get() = unreadSince > 0
}

internal data class FriendHistoryImportResult(
    val friendAdded: Int = 0,
    val friendSkipped: Int = 0,
    val eventAdded: Int = 0,
    val eventSkipped: Int = 0,
    val exclusionAdded: Int = 0,
    val exclusionSkipped: Int = 0,
) {
    val summary: String
        get() = "导入完成：好友快照 +$friendAdded（跳过 $friendSkipped），" +
                "删除记录 +$eventAdded（跳过 $eventSkipped），" +
                "排除名单 +$exclusionAdded（跳过 $exclusionSkipped）"
}

/** 手动添加记录的结果：同一 QQ 号只允许一条记录，已存在时不写入、不覆盖。 */
internal enum class ManualAddResult {
    /** 写入成功。 */
    ADDED,

    /** 该 QQ 号已有记录（任意来源），拒绝写入。 */
    DUPLICATE,

    /** QQ 号格式不合法。 */
    INVALID,
}

internal object FriendHistoryStore {

    internal const val MAX_EVENTS = 2000
    private const val MAX_UIN_LENGTH = 12

    /** 跨进程锁的最长等待：超时降级为不加锁，绝不无期限阻塞。 */
    private const val LOCK_WAIT_TIMEOUT_MS = 300L
    private const val LOCK_RETRY_GAP_MS = 5L

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** 每个进程内串行化读写，避免 hook 线程与界面线程同时改同一个文件。 */
    private val lock = Any()

    internal fun fileOf(uin: String): File =
        File(HookEnv.moduleDataPath, "account/$uin/friend_history.json")

    internal fun load(uin: String): FriendHistorySnapshot = synchronized(lock) {
        val raw = loadUnlocked(uin)
        if (raw.version >= CURRENT_VERSION) return raw
        val migrated = migrate(raw)
        // 迁移必须落盘：旧实现只在内存里改，文件里的 version 永远停在 1，
        // 于是每一次 load 都重跑一遍迁移、而且每次都追加一条 diag 日志。
        // 这是一次性的初始化，写一次就结束。
        saveUnlocked(uin, migrated)
        migrated
    }

    /** 各进程的 hook 安装结果（主进程 / :MSF），界面据此自证检测链路是否真的挂上了。 */
    @Serializable
    internal data class HookStatus(
        val main: String = "",
        val msf: String = "",
        val updatedAt: Long = 0,
    )

    private fun statusFile(): File = File(HookEnv.moduleDataPath, "friend_history_status.json")

    internal fun recordHookStatus(tag: String, text: String) {
        synchronized(lock) {
            // 主进程与 :MSF 会先后写入各自那一栏，读改写必须跨进程串行，否则后写的会把先写的覆盖回“未安装”。
            withProcessLock {
                runCatching {
                    val file = statusFile()
                    val current = if (file.isFile) {
                        json.decodeFromString(HookStatus.serializer(), file.readText(Charsets.UTF_8))
                    } else {
                        HookStatus()
                    }
                    val next = if (tag == "msf") {
                        current.copy(msf = text, updatedAt = nowSec())
                    } else {
                        current.copy(main = text, updatedAt = nowSec())
                    }
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(HookStatus.serializer(), next), Charsets.UTF_8)
                }.onFailure { Log.e("好友历史：写入 hook 状态失败", it) }
            }
        }
    }

    internal fun readHookStatus(): HookStatus = synchronized(lock) {
        withProcessLock {
            runCatching {
                val file = statusFile()
                if (!file.isFile) {
                    HookStatus()
                } else {
                    json.decodeFromString(HookStatus.serializer(), file.readText(Charsets.UTF_8))
                }
            }.getOrDefault(HookStatus())
        }
    }

    /**
     * 一次「立即刷新好友列表」的进度。
     *
     * 按钮在主进程被点击，但好友列表分片可能落在 :MSF，所以进度必须落盘而不是放在进程内变量，
     * 否则界面永远看不到“已收到分片”，也无法判断是没发出去还是没回包。
     */
    @Serializable
    internal data class FriendHistoryRefreshStatus(
        val accountUin: String = "",
        /** idle / requested / receiving / done / timeout / failed */
        val state: String = STATE_IDLE,
        /** 发起时间（秒），用于计算耗时与超时提示。 */
        val startedAt: Long = 0,
        /** 本轮已收到并拼接的分片数。 */
        val chunks: Int = 0,
        /** 本轮已解析到的好友条数。 */
        val parsedFriends: Int = 0,
        /** 服务器声明的好友总数；0 表示还没拿到。 */
        val total: Int = 0,
        val finishedAt: Long = 0,
        val message: String = "",
    ) {
        val active: Boolean get() = state == STATE_REQUESTED || state == STATE_RECEIVING
        val finished: Boolean get() = state == STATE_DONE || state == STATE_TIMEOUT || state == STATE_FAILED

        companion object {
            const val STATE_IDLE = "idle"
            const val STATE_REQUESTED = "requested"
            const val STATE_RECEIVING = "receiving"
            const val STATE_DONE = "done"
            const val STATE_TIMEOUT = "timeout"
            const val STATE_FAILED = "failed"
        }
    }

    private fun refreshFile(): File = File(HookEnv.moduleDataPath, "friend_history_refresh.json")

    internal fun readRefreshStatus(): FriendHistoryRefreshStatus = synchronized(lock) {
        runCatching {
            val file = refreshFile()
            if (!file.isFile) {
                FriendHistoryRefreshStatus()
            } else {
                json.decodeFromString(
                    FriendHistoryRefreshStatus.serializer(),
                    file.readText(Charsets.UTF_8),
                )
            }
        }.getOrDefault(FriendHistoryRefreshStatus())
    }

    private fun writeRefreshStatus(status: FriendHistoryRefreshStatus) {
        runCatching {
            val file = refreshFile()
            file.parentFile?.mkdirs()
            val text = json.encodeToString(FriendHistoryRefreshStatus.serializer(), status)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) error("无法写入 ${file.absolutePath}")
            }
        }.onFailure {
            Log.e("好友历史：保存刷新状态失败", it)
            diag("刷新状态写入失败：${it.message}")
        }
    }

    internal fun markRefreshStarted(uin: String, message: String): FriendHistoryRefreshStatus {
        val next = FriendHistoryRefreshStatus(
            accountUin = uin,
            state = FriendHistoryRefreshStatus.STATE_REQUESTED,
            startedAt = nowSec(),
            message = message,
        )
        synchronized(lock) { writeRefreshStatus(next) }
        return next
    }

    /** 分片回调里调用；没有进行中的手动刷新时直接忽略，避免自然同步覆盖界面状态。 */
    internal fun markRefreshChunk(
        uin: String,
        chunks: Int,
        parsedFriends: Int,
        total: Int,
    ): FriendHistoryRefreshStatus? = synchronized(lock) {
        val current = readRefreshStatus()
        if (current.accountUin != uin || !current.active) return null
        val next = current.copy(
            state = FriendHistoryRefreshStatus.STATE_RECEIVING,
            chunks = maxOf(current.chunks, chunks),
            parsedFriends = maxOf(current.parsedFriends, parsedFriends),
            total = if (total > 0) total else current.total,
            message = "已收到 ${maxOf(current.chunks, chunks)} 个分片",
        )
        writeRefreshStatus(next)
        next
    }

    internal fun markRefreshDone(
        uin: String,
        friendCount: Int,
        message: String = "",
    ): FriendHistoryRefreshStatus? = synchronized(lock) {
        val current = readRefreshStatus()
        if (current.accountUin != uin || !current.active) return null
        val elapsed = (nowSec() - current.startedAt).coerceAtLeast(0)
        val next = current.copy(
            state = FriendHistoryRefreshStatus.STATE_DONE,
            finishedAt = nowSec(),
            parsedFriends = friendCount,
            message = message.ifBlank { "刷新完成：好友 $friendCount 个，用时 $elapsed 秒" },
        )
        writeRefreshStatus(next)
        next
    }

    /** 失败/超时收尾；只有仍在进行中的刷新才会被改写，避免迟到的回调覆盖已完成状态。 */
    internal fun markRefreshProblem(
        uin: String,
        state: String,
        message: String,
    ): FriendHistoryRefreshStatus? = synchronized(lock) {
        val current = readRefreshStatus()
        if (current.accountUin != uin || !current.active) return null
        val next = current.copy(state = state, finishedAt = nowSec(), message = message)
        writeRefreshStatus(next)
        next
    }

    /** 记录一次“登录读取人数并比对”的结果。 */
    internal fun recordCountCheck(uin: String, result: String) {
        if (uin.isBlank()) return
        update(uin) { it.copy(lastCountCheckAt = nowSec(), lastCountCheckResult = result) }
    }

    /** 记录一次主动刷新的结果，界面用它确认“刷新到底有没有发出去”。 */
    internal fun markAttempt(uin: String, result: String) {
        if (uin.isBlank()) return
        update(uin) { it.copy(lastAttemptAt = nowSec(), lastAttemptResult = result) }
    }


    /**
     * 读改写。主进程与 MSF 都会处理好友列表回包，两个进程可能同时写同一份文件；
     * [lock] 只能保证进程内互斥，所以这里再叠加一层跨进程文件锁，避免“最后写入覆盖前一次检测”。
     */
    internal fun update(
        uin: String,
        block: (FriendHistorySnapshot) -> FriendHistorySnapshot,
    ): FriendHistorySnapshot = synchronized(lock) {
        withProcessLock {
            val base = migrate(loadUnlocked(uin))
            val next = block(base)
            saveUnlocked(uin, next)
            syncUnreadFlag(uin, base.unreadSince, next.unreadSince)
            next
        }
    }

    private fun unreadFlagFile(uin: String): File =
        File(HookEnv.moduleDataPath, "account/$uin/friend_history.unread")

    /**
     * 入口红点用独立的小标记文件。
     *
     * 这样设置列表每次重组只需要一次 `exists()`，不用把整份好友历史 JSON 读进内存；
     * 标记与快照里的 `unreadSince` 由这里统一同步，不会出现两套口径。
     */
    private fun syncUnreadFlag(uin: String, previous: Long, next: Long) {
        if ((previous > 0) == (next > 0)) return
        runCatching {
            val file = unreadFlagFile(uin)
            if (next > 0) {
                file.parentFile?.mkdirs()
                file.writeText(next.toString(), Charsets.UTF_8)
            } else {
                file.delete()
            }
        }.onFailure { Log.w("好友历史：更新未读标记失败（${it.message}）") }
    }

    /**
     * 旧结构平滑迁移：只补字段，不改已有事实。
     *
     * - 好友：缺 [FriendHistoryRecord.status] 的按 `present/deletedAt` 推导后写回；
     * - 事件：缺 [FriendHistoryEvent.type] 的按 `source` 推导后写回；`timeRangeBegin` 保持 0
     *   （旧数据没有上一次同步时间，界面退化为只显示检测时间点，不编造区间）；
     * - 未读：旧数据的变动早已被用户看过，迁移时不置未读，避免升级后突然冒红点。
     *
     * 这段逻辑是**幂等**的：version 已经是当前值时直接返回。
     */
    private fun migrate(snapshot: FriendHistorySnapshot): FriendHistorySnapshot {
        if (snapshot.version >= CURRENT_VERSION) return snapshot
        val friends = snapshot.friends.mapValues { (_, record) ->
            if (record.status != FriendHistoryRecord.STATUS_UNKNOWN) {
                record
            } else {
                record.copy(status = record.resolvedStatus)
            }
        }
        val events = snapshot.events.map { event ->
            if (event.type != FriendHistoryEvent.TYPE_UNKNOWN) event else event.copy(type = event.resolvedType)
        }
        diag("迁移 v${snapshot.version} → v$CURRENT_VERSION：好友 ${friends.size}，事件 ${events.size}")
        return snapshot.copy(
            version = CURRENT_VERSION,
            friends = friends,
            events = events,
            unreadSince = 0,
        )
    }

    /**
     * 跨进程文件锁；文件锁不可用（部分沙盒/只读环境）时退回进程内锁，不阻断功能。
     *
     * 只能用 `tryLock` 轮询：`FileLock.lock()` 是**无超时阻塞**，另一个进程只要
     * 卡在自己的临界区里（例如被 GC 暂停或正在做首次 dex 验证），这里就会一直等下去，
     * 既不抛异常也不返回——宿主主线程一旦走到这条路径就是整屏卡死。
     * 超时后不加锁继续执行：极小概率互相覆盖，远好于卡死。
     */
    private fun <T> withProcessLock(block: () -> T): T {
        val lockFile = File(HookEnv.moduleDataPath, "friend_history.lock")
        val handle = runCatching {
            lockFile.parentFile?.mkdirs()
            java.io.RandomAccessFile(lockFile, "rw")
        }.getOrNull() ?: return block()
        return handle.use { access ->
            var fileLock: FileLock? = null
            val deadline = SystemClock.elapsedRealtime() + LOCK_WAIT_TIMEOUT_MS
            try {
                while (true) {
                    fileLock = runCatching { access.channel.tryLock() }.getOrNull()
                    if (fileLock != null || SystemClock.elapsedRealtime() >= deadline) break
                    Thread.sleep(LOCK_RETRY_GAP_MS)
                }
                block()
            } finally {
                runCatching { fileLock?.release() }
            }
        }
    }

    /**
     * 手动添加一条记录。
     *
     * [remark] 是用户手填的备注（优先展示）；[nick] 是手填昵称留空时自动查询到的昵称。
     * 两者都为空时列表按 QQ 号展示，与自动检测出的记录口径一致。
     *
     * 同一 QQ 号只允许一条记录：已存在（不论来自同步、主动删除、新增回调还是手动）时直接返回
     * [ManualAddResult.DUPLICATE]，既不新增第二条，也不覆盖原有备注与时间。
     */
    internal fun addManualEvent(
        uin: String,
        targetUin: String,
        remark: String,
        nick: String = "",
        nowSec: Long = nowSec(),
    ): ManualAddResult {
        val target = normalizeUin(targetUin) ?: return ManualAddResult.INVALID
        var result = ManualAddResult.ADDED
        update(uin) { snapshot ->
            if (snapshot.events.any { it.uin == target }) {
                result = ManualAddResult.DUPLICATE
                return@update snapshot
            }
            val event = FriendHistoryEvent(
                uin = target,
                remark = remark.trim(),
                nick = nick.trim(),
                detectedAt = nowSec,
                seenAt = nowSec,
                source = FriendHistoryEvent.SOURCE_MANUAL,
                // 手动补录的是一条“被删”记录：类型固定为删除，徽标仍按当前状态现场推导。
                type = FriendHistoryEvent.TYPE_DELETE,
            )
            snapshot.copy(
                events = trimEvents(listOf(event) + snapshot.events),
                unreadSince = maxOf(snapshot.unreadSince, nowSec),
            )
        }
        return result
    }

    internal fun clearEvents(uin: String) = update(uin) { it.copy(events = emptyList()) }

    /** 打开记录页后清掉入口红点；没有未读时不写盘。 */
    internal fun clearUnread(uin: String) = update(uin) { snapshot ->
        if (snapshot.unreadSince == 0L) snapshot else snapshot.copy(unreadSince = 0)
    }

    /** 入口红点状态：只看标记文件，读不到一律按“无未读”处理。 */
    internal fun hasUnread(uin: String): Boolean =
        uin.isNotBlank() && unreadFlagFile(uin).isFile

    /**
     * 好友新增后立刻写入快照与人数基线（由宿主新增好友回调触发，不再等下一轮定时比对）。
     *
     * 只更新这一位好友：`present=true`、补齐昵称/备注/头像，并把 [FriendHistorySnapshot.lastFriendCount]
     * 同步到最新人数；同时补一条“新增好友”事件，历史列表立刻可见。
     *
     * 删除判定与通知逻辑不变：这里既不标记删除，也不发通知。
     *
     * @return 是否真的新增了一条记录（好友本来就在快照里时不重复记）
     */
    internal fun markFriendAdded(
        uin: String,
        targetUin: String,
        nick: String = "",
        remark: String = "",
        nowSec: Long = nowSec(),
    ): Boolean {
        val target = normalizeUin(targetUin) ?: return false
        var eventAdded = false
        update(uin) { snapshot ->
            if (target in snapshot.exclusionSet) return@update snapshot
            val old = snapshot.friends[target]
            eventAdded = old?.inList != true
            val record = FriendHistoryRecord(
                uin = target,
                nick = nick.ifBlank { old?.nick.orEmpty() },
                remark = remark.ifBlank { old?.remark.orEmpty() },
                avatar = FriendSnapshotReader.avatarOf(target),
                seenAt = nowSec,
                present = true,
                deletedAt = 0,
                status = FriendHistoryRecord.STATUS_FRIEND,
            )
            val friends = snapshot.friends + (target to record)
            // 基线未建立时不改人数：首轮全量同步会重建完整基线，这里只先落一条明细。
            val presentCount = if (snapshot.initialized) friends.values.count { it.present } else snapshot.lastFriendCount
            snapshot.copy(
                accountUin = uin,
                friends = friends,
                events = if (eventAdded) {
                    trimEvents(
                        listOf(
                            FriendHistoryEvent(
                                uin = target,
                                nick = record.nick,
                                remark = record.remark,
                                detectedAt = nowSec,
                                seenAt = nowSec,
                                source = FriendHistoryEvent.SOURCE_ADDED,
                                type = FriendHistoryEvent.TYPE_ADD,
                            )
                        ) + snapshot.events
                    )
                } else {
                    snapshot.events
                },
                unreadSince = if (eventAdded) maxOf(snapshot.unreadSince, nowSec) else snapshot.unreadSince,
                lastFriendCount = presentCount,
            )
        }
        return eventAdded
    }

    /** 删除一条记录（按 uin + 检测时间 + 来源定位），返回是否真的删掉了。 */
    internal fun removeEvent(uin: String, target: FriendHistoryEvent): Boolean {
        var removed = false
        update(uin) { snapshot ->
            val next = snapshot.events.filterNot {
                it.uin == target.uin &&
                    it.detectedAt == target.detectedAt &&
                    it.source == target.source
            }
            removed = next.size != snapshot.events.size
            snapshot.copy(events = next)
        }
        return removed
    }

    internal fun setExclusions(uin: String, exclusions: Collection<String>) = update(uin) { snapshot ->
        val normalized = exclusions.mapNotNull(::normalizeUin).distinct()
        snapshot.copy(
            exclusions = normalized.sorted(),
            // 被排除的人不再入库：从快照里移除，避免下次同步再次比对。
            friends = snapshot.friends.filterKeys { key -> key !in normalized },
        )
    }

    /**
     * 导出为可读 JSON：字段名、时间都按人可读的形式展开；账号、导出时间、排除名单一并带出。
     *
     * 时间使用 ISO-8601（本机时区），导入时同时接受 ISO 字符串与秒/毫秒时间戳。
     */
    internal fun exportText(uin: String): String {
        val snapshot = load(uin)

        val exclusionsArray = buildJsonArray {
            snapshot.exclusions.forEach { add(JsonPrimitive(it)) }
        }

        val friendsArray = buildJsonArray {
            snapshot.friends.values.sortedByDescending { it.seenAt }.forEach { record ->
                add(buildJsonObject {
                    put("uin", record.uin)
                    put("nick", record.nick)
                    put("remark", record.remark)
                    put("present", record.present)
                    put("status", record.resolvedStatus)
                    if (record.seenAt > 0) put("seenAt", isoTime(record.seenAt))
                    if (record.deletedAt > 0) put("deletedAt", isoTime(record.deletedAt))
                })
            }
        }

        val eventsArray = buildJsonArray {
            snapshot.events.forEach { event ->
                add(buildJsonObject {
                    put("uin", event.uin)
                    put("nick", event.nick)
                    put("remark", event.remark)
                    put("source", event.source)
                    put("type", event.resolvedType)
                    if (event.timeRangeBegin > 0) put("rangeBegin", isoTime(event.timeRangeBegin))
                    if (event.seenAt > 0) put("seenAt", isoTime(event.seenAt))
                    put("detectedAt", isoTime(event.detectedAt))
                })
            }
        }

        val root = buildJsonObject {
            put("format", "TCQT_FRIEND_HISTORY")
            put("version", snapshot.version)
            put("account", snapshot.accountUin.ifBlank { uin })
            put("exportedAt", isoTime(nowSec()))
            put("note", "时间为 ISO-8601；导入时也接受秒或毫秒时间戳")
            put("exclusions", exclusionsArray)
            put("friends", friendsArray)
            put("events", eventsArray)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * 容错导入：逐条校验，坏条目跳过并计数，不因单条损坏使整包失败。
     * 只有“根节点不是 JSON 对象”这种完全无法解析的情况才抛出异常。
     */
    internal fun importText(uin: String, text: String): FriendHistoryImportResult {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw IllegalArgumentException("不是有效的 JSON 对象：${it.message ?: "解析失败"}") }

        var friendAdded = 0
        var friendSkipped = 0
        var eventAdded = 0
        var eventSkipped = 0
        var exclusionAdded = 0
        var exclusionSkipped = 0

        val importedFriends = mutableListOf<FriendHistoryRecord>()
        root["friends"]?.let { element ->
            if (element !is JsonArray) {
                friendSkipped += 1
            } else {
                element.forEach { item ->
                    val record = runCatching { parseFriend(item) }.getOrNull()
                    if (record == null) friendSkipped += 1 else importedFriends += record
                }
            }
        }

        val importedEvents = mutableListOf<FriendHistoryEvent>()
        root["events"]?.let { element ->
            if (element !is JsonArray) {
                eventSkipped += 1
            } else {
                element.forEach { item ->
                    val event = runCatching { parseEvent(item) }.getOrNull()
                    if (event == null) eventSkipped += 1 else importedEvents += event
                }
            }
        }

        val importedExclusions = mutableListOf<String>()
        root["exclusions"]?.let { element ->
            if (element !is JsonArray) {
                exclusionSkipped += 1
            } else {
                element.forEach { item ->
                    val value = normalizeUin(item.asString())
                    if (value == null) exclusionSkipped += 1 else importedExclusions += value
                }
            }
        }

        update(uin) { snapshot ->
            val friends = snapshot.friends.toMutableMap()
            importedFriends.forEach { record ->
                val old = friends[record.uin]
                if (old == null) {
                    friends[record.uin] = record
                    friendAdded += 1
                } else if (record.seenAt > old.seenAt) {
                    friends[record.uin] = record.copy(present = record.present || old.present)
                    friendAdded += 1
                } else {
                    friendSkipped += 1
                }
            }

            val existingEventKeys = snapshot.events.mapTo(mutableSetOf(), ::eventKey)
            val mergedEvents = snapshot.events.toMutableList()
            importedEvents.forEach { event ->
                if (!existingEventKeys.add(eventKey(event))) {
                    eventSkipped += 1
                } else {
                    mergedEvents += event
                    eventAdded += 1
                }
            }

            val exclusions = snapshot.exclusions.toMutableSet()
            importedExclusions.forEach { value ->
                if (exclusions.add(value)) exclusionAdded += 1 else exclusionSkipped += 1
            }

            snapshot.copy(
                friends = friends.filterKeys { key -> key !in exclusions },
                events = trimEvents(mergedEvents),
                exclusions = exclusions.sorted(),
            )
        }

        return FriendHistoryImportResult(
            friendAdded = friendAdded,
            friendSkipped = friendSkipped,
            eventAdded = eventAdded,
            eventSkipped = eventSkipped,
            exclusionAdded = exclusionAdded,
            exclusionSkipped = exclusionSkipped,
        )
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────────

    private fun loadUnlocked(uin: String): FriendHistorySnapshot {
        val file = fileOf(uin)
        if (!file.isFile) return FriendHistorySnapshot(accountUin = uin)
        return runCatching {
            json.decodeFromString(FriendHistorySnapshot.serializer(), file.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e("好友历史：读取 $uin 失败，已重置为基线", error)
            runCatching {
                file.renameTo(File(file.parentFile, "${file.name}.bad-${System.currentTimeMillis()}"))
            }
            FriendHistorySnapshot(accountUin = uin)
        }
    }

    private fun saveUnlocked(uin: String, snapshot: FriendHistorySnapshot) {
        val file = fileOf(uin)
        runCatching {
            file.parentFile?.mkdirs()
            val text = json.encodeToString(FriendHistorySnapshot.serializer(), snapshot)
            // 先写临时文件再改名：宿主进程被强杀时不至于留下半个 JSON。
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) error("无法写入 ${file.absolutePath}")
            }
        }.onFailure {
            Log.e("好友历史：保存 $uin 失败", it)
            diag("保存失败 uin=$uin：${it.message}")
        }
    }

    private fun trimEvents(events: Collection<FriendHistoryEvent>): List<FriendHistoryEvent> =
        events.sortedByDescending { it.detectedAt }.take(MAX_EVENTS)

    private fun eventKey(event: FriendHistoryEvent): String =
        "${event.uin}|${event.detectedAt}|${event.source}"

    private fun parseFriend(element: JsonElement): FriendHistoryRecord? {
        val obj = element as? JsonObject ?: return null
        val uin = normalizeUin(obj["uin"].asString()) ?: return null
        return FriendHistoryRecord(
            uin = uin,
            nick = obj.stringValue("nick"),
            remark = obj.stringValue("remark"),
            seenAt = parseTime(obj["seenAt"]) ?: 0,
            present = obj.boolValue("present") ?: false,
            deletedAt = parseTime(obj["deletedAt"]) ?: 0,
            status = obj.intValue("status") ?: FriendHistoryRecord.STATUS_UNKNOWN,
        )
    }

    private fun parseEvent(element: JsonElement): FriendHistoryEvent? {
        val obj = element as? JsonObject ?: return null
        val uin = normalizeUin(obj["uin"].asString()) ?: return null
        val detectedAt = parseTime(obj["detectedAt"]) ?: return null
        var source = obj.stringValue("source").ifBlank { FriendHistoryEvent.SOURCE_SYNC }
        if (source !in VALID_SOURCES) source = FriendHistoryEvent.SOURCE_SYNC
        return FriendHistoryEvent(
            uin = uin,
            nick = obj.stringValue("nick"),
            remark = obj.stringValue("remark"),
            detectedAt = detectedAt,
            seenAt = parseTime(obj["seenAt"]) ?: 0,
            source = source,
            type = obj.intValue("type") ?: FriendHistoryEvent.TYPE_UNKNOWN,
            timeRangeBegin = parseTime(obj["rangeBegin"]) ?: 0,
        )
    }

    private val VALID_SOURCES = setOf(
        FriendHistoryEvent.SOURCE_SYNC,
        FriendHistoryEvent.SOURCE_ACTIVE,
        FriendHistoryEvent.SOURCE_MANUAL,
        FriendHistoryEvent.SOURCE_ADDED,
    )

    private fun JsonObject.stringValue(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

    private fun JsonObject.boolValue(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()

    private fun JsonObject.intValue(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()

    private fun JsonElement?.asString(): String? = when (this) {
        is JsonPrimitive -> content.trim()
        else -> null
    }

    /** 同时接受 ISO-8601 字符串与秒/毫秒时间戳，坏值返回 null。 */
    private fun parseTime(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) {
            val text = primitive.content.trim()
            return runCatching { Instant.parse(text).epochSecond }.getOrElse {
                runCatching {
                    java.time.OffsetDateTime.parse(text).toEpochSecond()
                }.getOrElse {
                    text.toLongOrNull()?.let(::normalizeEpoch)
                }
            }
        }
        return primitive.content.toLongOrNull()?.let(::normalizeEpoch)
    }

    private fun normalizeEpoch(value: Long): Long =
        if (value > 10_000_000_000L) value / 1000 else value

    private fun normalizeUin(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.length !in 3..MAX_UIN_LENGTH) return null
        if (!value.all(Char::isDigit)) return null
        return value
    }

    private fun isoTime(epochSec: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
        )

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    /** 界面展示用的相对时间，如“3 分钟前”。 */
    internal fun relativeTime(epochSec: Long): String {
        if (epochSec <= 0) return "未知时间"
        val diff = (nowSec() - epochSec).coerceAtLeast(0)
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86_400 -> "${diff / 3600} 小时前"
            diff < 30 * 86_400 -> "${diff / 86_400} 天前"
            else -> absoluteTime(epochSec)
        }
    }

    internal fun absoluteTime(epochSec: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))

    /**
     * 事件的时间文案：能确定检测窗口时显示「起 ~ 止」，否则退化为单点。
     *
     * 同一天内只写一次日期，例如 `2026-11-13 08:00 ~ 09:30`；
     * 旧数据没有区间（[FriendHistoryEvent.timeRangeBegin] 为 0）时显示成 `3 天前检测`。
     */
    internal fun timeRangeText(begin: Long, end: Long): String {
        if (end <= 0) return "时间未知"
        if (begin <= 0 || begin >= end) return "${relativeTime(end)}检测"
        val zone = ZoneId.systemDefault()
        val beginDay = Instant.ofEpochSecond(begin).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochSecond(end).atZone(zone).toLocalDate()
        return if (beginDay == endDay) {
            "${absoluteTime(begin)} ~ " + DateTimeFormatter.ofPattern("HH:mm")
                .format(Instant.ofEpochSecond(end).atZone(zone))
        } else {
            "${absoluteTime(begin)} ~ ${absoluteTime(end)}"
        }
    }

    /**
     * 自动落盘的轻量诊断日志：`<moduleDataPath>/friend_history_diag.log`。
     *
     * 记录 hook 安装结果、每轮分片、跳过原因与通知决策；超过 256KB 自动清空，
     * 不需要用户手动操作，出问题时直接把这个文件取出来即可。
     */
    internal fun diag(text: String) {
        runCatching {
            val file = File(HookEnv.moduleDataPath, "friend_history_diag.log")
            file.parentFile?.mkdirs()
            if (file.length() > 256 * 1024) file.delete()
            file.appendText("${absoluteTime(nowSec())} $text\n", Charsets.UTF_8)
        }
    }
}
