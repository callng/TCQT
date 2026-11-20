package com.owo233.tcqt.host.service

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qroute.QRouteApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 好友历史与“被删好友”检测。
 *
 * 检测方式与 QAuxiliary 一致：好友列表由宿主按分片回包（`friendlist.GetFriendListResp`），
 * 我们把同一次同步的所有分片拼齐后，与本地快照做差集。**不轮询、不起服务、不依赖 Xposed API**，
 * 因此 LSPosed 与 Zygisk 两种注入方式都能工作。
 *
 * 只为“风险最小”的目标 hook：`readFrom` 的 After 回调，回调内部所有异常都被吞掉，
 * 任何反射失配都只会让本功能停在“只记录不提示”，不会影响宿主收发消息与好友列表。
 */
internal object FriendHistoryManager {

    /** 与 [com.owo233.tcqt.features.friendhistory.FriendDeletionNotification] 共用的开关 key。 */
    const val NOTIFY_KEY = "friend_deletion_notification"

    /** 与 [com.owo233.tcqt.features.friendhistory.NewFriendNotification] 共用的开关 key。 */
    const val NEW_FRIEND_NOTIFY_KEY = "new_friend_notification"

    private const val NOTIFY_ID = 10001
    private const val CHANNEL_ID = "tcqt_friend_deleted"
    private const val CHANNEL_NAME = "被删好友提醒"

    /** 新增好友用独立渠道与 id 段，文案、分组都和被删提醒分开。 */
    private const val ADDED_CHANNEL_ID = "tcqt_friend_added"
    private const val ADDED_CHANNEL_NAME = "新增好友提醒"
    private const val ADDED_NOTIFY_ID_BASE = 11000

    /** 同一位好友的「新增提醒」去重窗口：加好友回包与全量同步可能同时命中同一个人。 */
    private const val ADDED_NOTIFY_DEDUP_MS = 10 * 60 * 1000L
    private val addedNotifiedAt = ConcurrentHashMap<String, Long>()
    private const val MIN_TOTAL_FRIENDS = 2

    /** 登录后首次读人数：给好友缓存留出加载时间。 */
    private const val LOGIN_FIRST_DELAY_MS = 20_000L

    /** 读不到人数时的重试间隔与次数。 */
    private const val LOGIN_RETRY_GAP_MS = 30_000L
    private const val LOGIN_MAX_ATTEMPTS = 5

    /** 长时间在线时的兜底人数检测。 */
    private const val PERIODIC_REFRESH_GAP_MS = 10 * 60 * 1000L

    /**
     * 人数与上次相同也强制读一次明细的间隔。
     *
     * 只比人数会漏掉“删了一个、又加了一个”的净变化为 0 的情况，所以超过这个间隔就做一次全量差集。
     */
    private const val FORCE_DETAIL_INTERVAL_SEC = 30 * 60L

    /**
     * 进行中的刷新超过这个时间仍未收尾，就视为过期。
     *
     * 上次进程被杀、反射调用卡死时，盘上的状态会一直停在 requested/receiving；
     * 不做过期判断的话，之后再点按钮永远只会得到「正在刷新中」。
     */
    private const val STALE_REFRESH_SEC = 180L

    private const val FRIEND_HANDLER_NAME = "com.tencent.mobileqq.app.FriendListHandler"

    /** 新增好友回包可能早于宿主好友缓存更新，稍后再确认一次，避免把“只发出请求”误记成好友。 */
    private const val NEW_FRIEND_CONFIRM_DELAY_MS = 3_000L

    /** 好友列表变更通知的合并间隔：一次变更通常连着多次 notifyUI。 */
    private const val NEW_FRIEND_CHECK_GAP_MS = 5_000L

    /** 回前台轻量检测的最小间隔：频繁切前后台不叠加多次检测。 */
    private const val FOREGROUND_CHECK_GAP_MS = 60_000L

    /** 单步 hook 安装的最长耗时：超时只记诊断并继续，绝不拖住任何线程。 */
    private const val STEP_TIMEOUT_MS = 8_000L

    /** 相邻两步之间错开的时间，避免同时生成 bridge 与 patch ArtMethod 把宿主 CPU 打满。 */
    private const val STEP_GAP_MS = 120L

    /** 设置页状态文本的缓存时长：组合阶段只读缓存，不在主线程读文件。 */
    private const val STATUS_CACHE_MS = 5_000L

    private val installed = AtomicBoolean(false)

    /** 新增好友即时检测的节流时间戳（elapsedRealtime）。 */
    @Volatile
    private var lastNewFriendCheckAt = 0L

    /** 回前台轻量检测的节流时间戳（elapsedRealtime）。 */
    @Volatile
    private var lastForegroundCheckAt = 0L

    /** 处于 started 状态的 Activity 数量：0→1 即从后台回到前台。 */
    private val startedActivities = AtomicInteger(0)

    /** 人数检测互斥：登录检测、10 分钟周期、回前台检测同一时间只跑一个。 */
    private val countCheckRunning = AtomicBoolean(false)

    private val newFriendLock = Any()

    /** 分片累计用的进程内状态，只在 [chunkLock] 内读写。 */
    private val chunkLock = Any()
    private val pendingChunks = mutableListOf<FriendListChunk>()

    private data class ChunkContact(
        val uin: String,
        val nick: String,
        val remark: String,
        val avatar: String = "",
    )

    /** 一次全量比对的结果：当前人数、被删的、新增的。 */
    private data class SnapshotDiff(
        val count: Int,
        val deleted: List<FriendHistoryEvent>,
        val added: List<String>,
    )

    private data class FriendListChunk(
        val startIndex: Int,
        val friendCount: Int,
        val total: Int,
        val serverTime: Long,
        val contacts: List<ChunkContact>,
    ) {
        val endIndex: Int get() = startIndex + friendCount
    }

    /**
     * hook 安装专用线程池：单步卡死只废掉自己那条线程，不占用 [ModuleScope] 的 IO 池，
     * 也不会把后续步骤一起堵住。
     */
    private val installExecutor by lazy {
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "TCQT-FriendHook").apply { isDaemon = true }
        }
    }

    /**
     * 好友列表回包的后台解析队列（单线程、保序）。
     *
     * 分片要按 `startIndex` 连续拼接，必须串行；同时让 hook 回调本身零耗时。
     */
    private val respWorker by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "TCQT-FriendResp").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    /**
     * 安装入口：**只登记，立即返回**。
     *
     * 一次安装要做的事（loadClass、反射匹配、Zygisk 形态下 DexMaker 生成 bridge/backup、
     * 原生 ArtMethod patch、写状态文件）在“刚装完模块的第一次启动”时是秒级操作：
     * 宿主 dex 还没有 vdex 验证结果，DexKit 缓存也刚失效。
     *
     * 这些活以前直接跑在调用线程上。调用方是宿主主线程时（首帧之后的 Deferred 安装），
     * 首屏就会整段卡住——表现为停在聊天页、点什么都没反应、也不抛异常。
     *
     * 现在全部丢到后台，并且每一步单独设超时：单步超时只记诊断，不阻塞任何人。
     */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        ModuleScope.launchIO("好友历史安装") {
            runCatching { installSteps() }.onFailure {
                Log.e("好友历史：安装失败", it)
                FriendHistoryStore.diag("install 失败：${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    /**
     * 串行安装各 hook，每步独立超时。
     *
     * 只挂“回包/通知”这类低频、和好友列表一一对应的宿主方法。
     * 不再挂 SplashActivity.onResume：每一次回前台都要走一次原生桥，
     * 而 ActivityLifecycleCallbacks 已经覆盖同一时机，且完全不 hook 宿主方法。
     */
    private suspend fun installSteps() {
        FriendHistoryStore.diag("install start process=main")
        val state = listOf(
            runStep("friendlist") { installFriendListHook() },
            runStep("addfriend") { installAddFriendHook() },
            runStep("notify") { installFriendListNotifyHook() },
            runStep("delfriend") { installDelFriendHook() },
            runStep("foreground") { installForegroundLifecycle() },
        ).joinToString(";")
        FriendHistoryStore.recordHookStatus("main", state)
        FriendHistoryStore.diag("install state=$state")
        startLoginSync()
    }

    /**
     * 在当前线程之外执行一步安装并等待有限时间。
     *
     * 用 `Future.get(timeout)` 而不是 `withTimeout`：`withTimeout` 只能打断挂起点，
     * 对阻塞的类加载 / DexMaker / 文件锁完全无效，那种写法看着有超时其实会一直等。
     */
    private suspend fun runStep(name: String, block: () -> String): String {
        val startedAt = SystemClock.elapsedRealtime()
        val future = installExecutor.submit(Callable { block() })
        val result = try {
            future.get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            val message = "$name=超时(>${STEP_TIMEOUT_MS}ms)"
            Log.w("好友历史：安装 $message")
            FriendHistoryStore.diag("install $message")
            message
        } catch (error: Exception) {
            val cause = error.cause ?: error
            val message = "$name=异常(${cause.message ?: cause.javaClass.simpleName})"
            Log.e("好友历史：安装 $message", cause)
            message
        }
        // 首次启动（dex 还没验证结果）与后续启动的耗时差别就体现这一行，
        // 用来确认“首屏卡住”是否发生在安装阶段。
        val cost = SystemClock.elapsedRealtime() - startedAt
        if (cost >= 300L) FriendHistoryStore.diag("install $name 耗时 ${cost}ms")
        delay(STEP_GAP_MS)
        return result
    }

    /**
     * 每次登录（宿主进程启动）读一次好友人数并比对。
     *
     * 好友缓存可能还没加载完，所以前几次读不到人数会退避重试；只要读到一次就停止重试。
     * 之后每 10 分钟再轻量读一次人数，作为长时间在线时的兜底。
     */
    private fun startLoginSync() {
        ModuleScope.launchDelayed(LOGIN_FIRST_DELAY_MS) {
            var attempt = 0
            while (attempt < LOGIN_MAX_ATTEMPTS) {
                val needRetry = runCatching { checkFriendCount("登录检测") }
                    .onFailure { Log.w("好友历史：登录人数检测异常（${it.message}）") }
                    .getOrDefault(true)
                if (!needRetry) break
                attempt++
                if (attempt >= LOGIN_MAX_ATTEMPTS) {
                    FriendHistoryStore.diag("登录检测：重试 $attempt 次仍读不到人数")
                    break
                }
                delay(LOGIN_RETRY_GAP_MS)
            }
            while (true) {
                delay(PERIODIC_REFRESH_GAP_MS)
                runCatching { checkFriendCount("周期检测") }
                    .onFailure { Log.w("好友历史：周期人数检测异常（${it.message}）") }
            }
        }
    }

    // ── hook 安装 ──────────────────────────────────────────────────────────────

    private fun installFriendListHook(): String {
        val respClass = load("friendlist.GetFriendListResp")
            ?: load("com.tencent.mobileqq.friendlist.GetFriendListResp")
            ?: run {
                Log.e("好友历史：未找到 friendlist.GetFriendListResp，停用检测")
                FriendHistoryStore.diag("hook=friendlist 缺失类 GetFriendListResp")
                return "friendlist=缺失类"
            }
        val jceClass = load("com.qq.taf.jce.JceInputStream") ?: run {
            Log.e("好友历史：未找到 JceInputStream，停用检测")
            FriendHistoryStore.diag("hook=friendlist 缺失类 JceInputStream")
            return "friendlist=缺失JceInputStream"
        }
        val readFrom = respClass.methods.firstOrNull {
            it.name == "readFrom" && it.parameterTypes.size == 1 && it.parameterTypes[0] == jceClass
        } ?: run {
            Log.e("好友历史：GetFriendListResp.readFrom 签名不匹配，停用检测")
            FriendHistoryStore.diag("hook=friendlist readFrom 签名不匹配")
            return "friendlist=readFrom签名不匹配"
        }
        // 回调内部自行吞异常：hook 框架会把未捕获异常重新抛给宿主调用点。
        // 这里只做入队：解析（逐个好友反射读字段）与落盘都在后台单线程里做，
        // 宿主的主线程/回包线程只承担一次投递，不会被本功能拖慢。
        readFrom.hookAfter { param ->
            val resp = param.thisObject
            if (resp != null) enqueueFriendResp("friendlist") { handleFriendListResponse(resp) }
        }
        FriendHistoryStore.diag("hook=friendlist ok（${respClass.name}）")
        return "friendlist=ok"
    }

    /**
     * 新增好友回包：拿到对方 QQ 号后立刻写入好友历史（首选路径）。
     *
     * 回包里没有目标 QQ 号或结果失败时静默跳过，由 [installFriendListNotifyHook] 的人数兜底补上。
     */
    private fun installAddFriendHook(): String {
        val jceClass = load("com.qq.taf.jce.JceInputStream") ?: run {
            FriendHistoryStore.diag("hook=addfriend 缺失JceInputStream")
            return "addfriend=缺失JceInputStream"
        }
        val hookedClasses = mutableListOf<String>()
        for (name in ADD_FRIEND_RESP_CLASSES) {
            val clazz = load(name) ?: continue
            val readFrom = clazz.methods.firstOrNull {
                it.name == "readFrom" && it.parameterTypes.size == 1 && it.parameterTypes[0] == jceClass
            } ?: continue
            readFrom.hookAfter { param ->
                val resp = param.thisObject
                if (resp != null) enqueueFriendResp("addfriend") { handleAddFriendResponse(resp) }
            }
            hookedClasses += name.substringAfterLast('.')
        }
        val state = if (hookedClasses.isEmpty()) {
            "addfriend=缺失回包"
        } else {
            "addfriend=${hookedClasses.joinToString("/")}"
        }
        FriendHistoryStore.diag("hook=$state")
        return state
    }

    /**
     * 人数兜底：宿主好友列表变更通知都触发一次“人数是否增加”的即时检测。
     *
     * 只挂 `FriendListHandler` 自己声明的 notifyUI 重载。
     * 不退回 `BusinessHandler.notifyUI`：那是所有业务 Handler 的基类，一次会给整条
     * 基类的每个 notifyUI 挂上原生桥，全应用每次回包都要过一遍桥再被过滤掉，
     * 既没有收益，又把打卡/消息这类高频路径一起暴露在原生派发里。
     */
    private fun installFriendListNotifyHook(): String {
        val handlerClass = load(FRIEND_HANDLER_NAME)
        val hooked = if (handlerClass != null) hookNotifyUi(handlerClass) else 0
        val state = when {
            hooked > 0 -> "notify=ok($hooked)"
            handlerClass == null -> "notify=缺失类"
            else -> "notify=无可挂方法"
        }
        FriendHistoryStore.diag("hook=$state")
        return state
    }

    /**
     * 挂 `FriendListHandler.notifyUI` 里**参数最少**的那一个重载。
     *
     * 一次好友列表变更会连着触发多个 notifyUI 重载，而 notifyUI 运行在宿主主线程上；
     * Zygisk 形态下每个被挂的方法每次调用都是一次原生桥派发（进 Java 桥方法 → 反射取
     * 返回类型/参数类型再回到 native）。挂满所有重载等于把主线程的派发次数翻几倍，
     * 收益却为零——只要命中一次就够触发检测。因此这里只保留一个入口。
     */
    private fun hookNotifyUi(clazz: Class<*>): Int {
        val target = clazz.declaredMethods
            .filter { it.name == "notifyUI" }
            .minByOrNull { it.parameterTypes.size }
            ?: return 0
        return runCatching {
            target.hookAfter { scheduleNewFriendCheck("list-notify") }
            1
        }.onFailure {
            Log.w("好友历史：挂 ${clazz.name}.notifyUI 失败（仅影响新增感知速度）", it)
        }.getOrDefault(0)
    }

    /**
     * 好友列表回包的统一投递点：hook 回调里**只允许**调用它。
     *
     * 解析一个分片要做几十到几百次反射读字段，加上快照 JSON 的读改写；
     * 这些放在宿主回调线程（很多时候就是主线程）上，一旦遇到首次启动的类验证
     * 或跨进程文件锁，就是整屏卡死。投递到单线程队列后顺序仍然有保证。
     */
    private fun enqueueFriendResp(tag: String, block: () -> Unit) {
        noteIfMainThread(tag)
        ModuleScope.launch(respWorker) {
            runCatching(block).onFailure {
                Log.e("好友历史：解析 $tag 回包失败", it)
                FriendHistoryStore.diag("hook=$tag 回调异常：${it.message}")
            }
        }
    }

    /** 诊断用：记录一次“宿主把回包回调发到了主线程”，同一种回包只记一条。 */
    private val mainThreadHits = ConcurrentHashMap.newKeySet<String>()

    private fun noteIfMainThread(tag: String) {
        if (!ModuleScope.isMainThread) return
        if (mainThreadHits.add(tag)) {
            FriendHistoryStore.diag("$tag 回包回调运行在主线程（解析已移到后台）")
        }
    }

    /**
     * 好友列表变更后的轻量入口：任意线程调用，只做节流与投递，实际比对在 IO 线程。
     */
    private fun scheduleNewFriendCheck(reason: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(newFriendLock) {
            if (now - lastNewFriendCheckAt < NEW_FRIEND_CHECK_GAP_MS) return
            lastNewFriendCheckAt = now
        }
        ModuleScope.launchIO("好友新增检测") {
            runCatching { checkNewFriends(reason) }
                .onFailure {
                    Log.w("好友历史：新增好友即时检测异常（${it.message}）")
                    FriendHistoryStore.diag(
                        "$reason：即时检测异常：${it.message ?: it.javaClass.simpleName}"
                    )
                }
        }
    }

    /**
     * 人数比基线多时立刻读明细补进基线（并补“新增好友”记录），不再等下一个 10 分钟周期。
     *
     * 人数没变就什么都不做：删除判定仍由原有的周期/全量流程负责，逻辑不变。
     */
    private fun checkNewFriends(label: String) {
        val accountUin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (accountUin.isBlank()) return
        val snapshot = FriendHistoryStore.load(accountUin)
        if (!snapshot.initialized) {
            FriendHistoryStore.diag("$label：基线未建立，交给登录检测建立完整基线")
            return
        }
        val count = FriendSnapshotReader.readCount()
        if (!count.ok) {
            FriendHistoryStore.diag("$label：人数读取失败，跳过即时检测（${count.detail}）")
            return
        }
        if (count.count <= snapshot.lastFriendCount) {
            FriendHistoryStore.diag(
                "$label：人数 ${count.count} 未超过基线 ${snapshot.lastFriendCount}，不读明细"
            )
            return
        }
        FriendHistoryStore.diag(
            "$label：人数 ${snapshot.lastFriendCount} → ${count.count}，立刻读明细补进基线"
        )
        val details = FriendSnapshotReader.readDetails()
        if (!details.ok) {
            FriendHistoryStore.diag("$label：明细读取失败（${details.detail}），等下一轮")
            return
        }
        val message = settleSnapshot(
            accountUin,
            details,
            "人数 ${count.count}（${count.source}）",
            force = true,
        )
        FriendHistoryStore.recordCountCheck(accountUin, "$label：$message")
    }

    /** 新增好友回包：结果成功且能解析出对方 QQ 号时立即写入。 */
    private fun handleAddFriendResponse(resp: Any?) {
        if (resp == null) return
        val result = (readField(resp, "result", "retCode", "ret_code") as? Number)?.toInt()
        val errorCode = (readField(resp, "errorCode", "error_code") as? Number)?.toInt()
        if ((result != null && result != 0) || (errorCode != null && errorCode != 0)) {
            FriendHistoryStore.diag("新增好友回包：result=$result errorCode=$errorCode，忽略")
            return
        }
        val targetUin = (readField(resp, "adduin", "addUin", "targetUin", "fuin") as? Number)
            ?.toLong()?.toString()
        if (targetUin.isNullOrBlank()) {
            FriendHistoryStore.diag("新增好友回包：没有目标 QQ 号，交给人数兜底")
            return
        }
        val accountUin = (readField(resp, "uin", "friendUin") as? Number)?.toLong()?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (accountUin.isBlank()) return
        persistNewFriend(accountUin, targetUin, "新增好友回包")
    }

    /**
     * 把新好友写进快照。
     *
     * 回包到达时宿主好友缓存可能还没更新：查不到明细就延后确认一次，仍查不到就放弃，
     * 由人数兜底/下一轮同步补。不能盲目写入——若其实只是“发出了好友请求”，
     * 下一轮全量同步会把它判成“被删”并误发通知。
     */
    private fun persistNewFriend(accountUin: String, targetUin: String, label: String) {
        ModuleScope.launchIO("好友新增写入") {
            var detail = runCatching { FriendSnapshotReader.lookupDetail(targetUin) }.getOrNull()
            if (detail == null) {
                delay(NEW_FRIEND_CONFIRM_DELAY_MS)
                detail = runCatching { FriendSnapshotReader.lookupDetail(targetUin) }.getOrNull()
            }
            val confirmed = detail
            if (confirmed == null) {
                FriendHistoryStore.diag("$label：$targetUin 尚未进入好友缓存，等人数兜底/下一轮同步")
                return@launchIO
            }
            val added = runCatching {
                FriendHistoryStore.markFriendAdded(
                    uin = accountUin,
                    targetUin = targetUin,
                    nick = confirmed.nick,
                    remark = confirmed.remark,
                )
            }.onFailure {
                Log.e("好友历史：写入新增好友失败", it)
                FriendHistoryStore.diag("$label：写入 $targetUin 失败：${it.message}")
            }.getOrDefault(false)
            FriendHistoryStore.diag(
                "$label：$targetUin " +
                    if (added) "已即时写入（${confirmed.nick.ifBlank { "无昵称" }}）"
                    else "已在快照中，只更新字段"
            )
            // 只有真正写入新的“新增好友”记录才提醒；已在快照里的人不重复通知。
            if (added) notifyFriendAdded(targetUin, confirmed.nick, confirmed.remark)
        }
    }

    private fun installDelFriendHook(): String {
        val respClass = load("friendlist.DelFriendResp") ?: run {
            Log.w("好友历史：未找到 friendlist.DelFriendResp，主动删除标记不可用")
            FriendHistoryStore.diag("hook=delfriend 缺失类")
            return "delfriend=缺失类"
        }
        val jceClass = load("com.qq.taf.jce.JceInputStream") ?: run {
            FriendHistoryStore.diag("hook=delfriend 缺失JceInputStream")
            return "delfriend=缺失JceInputStream"
        }
        val readFrom = respClass.methods.firstOrNull {
            it.name == "readFrom" && it.parameterTypes.size == 1 && it.parameterTypes[0] == jceClass
        } ?: run {
            FriendHistoryStore.diag("hook=delfriend readFrom签名不匹配")
            return "delfriend=readFrom签名不匹配"
        }
        readFrom.hookAfter { param ->
            val resp = param.thisObject
            if (resp != null) enqueueFriendResp("delfriend") { handleDelFriendResponse(resp) }
        }
        FriendHistoryStore.diag("hook=delfriend ok")
        return "delfriend=ok"
    }

    /** 主动删好友回包解析（在 [respWorker] 上执行）。 */
    private fun handleDelFriendResponse(resp: Any) {
        val accountUin = (readField(resp, "uin") as? Number)?.toLong()?.toString()
            ?: QQInterfaces.currentUin
        val targetUin = (readField(resp, "deluin") as? Number)?.toLong()?.toString() ?: return
        val result = (readField(resp, "result") as? Number)?.toInt() ?: -1
        val errorCode = (readField(resp, "errorCode") as? Number)?.toInt() ?: 1
        if (accountUin.isNotBlank() && result == 0 && errorCode == 0) {
            markActiveDelete(accountUin, targetUin)
        }
    }

    /**
     * 回前台挂载点：宿主 Application 的 ActivityLifecycleCallbacks。
     *
     * 只关心 Activity started 数量 0→1（真正从后台回到前台）；进程内打开聊天页等
     * 普通 Activity 切换不会触发。不 hook 任何宿主方法，版本无关、风险最低。
     */
    private fun installForegroundLifecycle(): String {
        val app = runCatching { HookEnv.hostAppContext as? Application }.getOrNull()
        if (app == null) {
            FriendHistoryStore.diag("hook=foreground 缺失Application")
            return "foreground=缺失Application"
        }
        val registered = runCatching {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    // 0→1 才是回前台；进程内 Activity 切换不会重复触发。
                    if (startedActivities.incrementAndGet() == 1) {
                        onHostForeground()
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities.decrementAndGet()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
            true
        }.getOrElse {
            Log.w("好友历史：注册前台生命周期失败（仅影响回前台感知）", it)
            false
        }
        val state = if (registered) "foreground=ok" else "foreground=注册失败"
        FriendHistoryStore.diag("hook=$state")
        return state
    }

    /**
     * 回前台轻量检测：只读人数，与基线一致就直接结束。
     *
     * 人数没变不读明细、不写记录、不弹提示；人数变了才转入现有的明细比对流程
     * （新增入缓存，减少则定位并通知）。
     */
    private fun onHostForeground() {
        val now = SystemClock.elapsedRealtime()
        synchronized(newFriendLock) {
            if (now - lastForegroundCheckAt < FOREGROUND_CHECK_GAP_MS) return
            lastForegroundCheckAt = now
        }
        ModuleScope.launchIO("好友回前台检测") {
            runCatching { checkForegroundCount() }
                .onFailure {
                    Log.w("好友历史：回前台人数检测异常（${it.message}）")
                    FriendHistoryStore.diag("回前台检测异常：${it.message ?: it.javaClass.simpleName}")
                }
        }
    }

    /**
     * 回前台检测主体：人数一致静默返回；人数变化才读明细走 [checkFriendCount]。
     *
     * 和 10 分钟周期检测共用 [countCheckRunning] 互斥，不会叠加执行。
     */
    private fun checkForegroundCount() {
        val accountUin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (accountUin.isBlank()) return
        val snapshot = FriendHistoryStore.load(accountUin)
        // 基线未建立时交给登录检测，避免这里只补一部分人形成残缺基线。
        if (!snapshot.initialized) return
        val count = FriendSnapshotReader.readCount()
        if (!count.ok) {
            FriendHistoryStore.diag("回前台：人数读取失败（${count.detail}）")
            return
        }
        if (count.count == snapshot.lastFriendCount) return
        FriendHistoryStore.diag(
            "回前台：人数 ${snapshot.lastFriendCount} → ${count.count}，转入明细比对"
        )
        checkFriendCount("回前台检测")
    }

    // ── 好友列表分片 → 差集 ────────────────────────────────────────────────────

    private fun handleFriendListResponse(resp: Any?) {
        if (resp == null) return
        val accountUin = (readField(resp, "uin", "friendUin") as? Number)?.toLong()?.toString()
            ?: QQInterfaces.currentUin
        if (accountUin.isBlank()) {
            FriendHistoryStore.diag("resp 丢弃：accountUin 为空")
            return
        }

        // 宿主的“拉取精简好友列表”回包没有好友数据，直接忽略。
        val getFriendCount =
            (readField(resp, "getfriendCount", "getFriendCount") as? Number)?.toInt() ?: 0
        if (getFriendCount == 0) {
            FriendHistoryStore.diag("resp 忽略：getfriendCount=0")
            return
        }

        val startIndex =
            (readField(resp, "startIndex", "start_index") as? Number)?.toInt() ?: 0
        val friendCount =
            (readField(resp, "friend_count", "friendCount") as? Number)?.toInt() ?: 0
        if (friendCount <= 0) {
            FriendHistoryStore.diag("resp 忽略：friend_count=$friendCount")
            return
        }
        val total = (readField(
            resp,
            "totoal_friend_count",
            "total_friend_count",
            "totalFriendCount",
        ) as? Number)?.toInt() ?: 0
        val serverTime =
            (readField(resp, "serverTime", "server_time") as? Number)?.toLong() ?: 0

        val contacts = readContacts(resp, friendCount)
        FriendHistoryStore.diag(
            "chunk start=$startIndex count=$friendCount total=$total parsed=${contacts.size}"
        )
        val chunk = FriendListChunk(startIndex, friendCount, total, serverTime, contacts)

        val ready: List<FriendListChunk>?
        var progressChunks = 0
        var progressParsed = 0
        var progressTotal = 0
        synchronized(chunkLock) {
            // 新的一轮同步从 0 开始：丢弃上一轮残留。
            if (startIndex == 0) pendingChunks.clear()
            // 序号不连续说明开头分片没被我们观察到，拼不齐就放弃这一轮。
            else if (pendingChunks.lastOrNull()?.endIndex != startIndex) pendingChunks.clear()

            pendingChunks += chunk
            progressChunks = pendingChunks.size
            progressParsed = pendingChunks.sumOf { it.contacts.size }
            progressTotal = pendingChunks.last().total
            val last = pendingChunks.last()
            ready = if (last.endIndex == total) {
                val chunks = pendingChunks.toList()
                pendingChunks.clear()
                if (chunks.first().startIndex != 0) null else chunks
            } else {
                null
            }
        }

        // 手动刷新的进度写盘：分片可能落在 :MSF，界面只读文件才能看到“已收到分片”。
        FriendHistoryStore.markRefreshChunk(
            uin = accountUin,
            chunks = progressChunks,
            parsedFriends = progressParsed,
            total = progressTotal,
        )

        val chunks = ready ?: return
        val sum = chunks.sumOf { it.friendCount }
        if (total < MIN_TOTAL_FRIENDS || total - sum != 0) {
            Log.i("好友历史：分片数量不一致（total=$total, sum=$sum），跳过本轮")
            FriendHistoryStore.diag("跳过本轮：分片不一致 total=$total sum=$sum")
            FriendHistoryStore.markRefreshProblem(
                accountUin,
                FriendHistoryStore.FriendHistoryRefreshStatus.STATE_FAILED,
                "分片数量对不上（total=$total，已收 $sum），本轮已跳过",
            )
            return
        }
        // 任何一条好友信息解析失败都会让差集误判为“被删”，因此条数对不上就整轮放弃。
        val current = chunks.asSequence()
            .flatMap { it.contacts.asSequence() }
            .associateBy { it.uin }
        if (current.size != total) {
            Log.i("好友历史：好友条数不一致（total=$total, parsed=${current.size}），跳过本轮")
            FriendHistoryStore.diag("跳过本轮：条数不一致 total=$total parsed=${current.size}")
            FriendHistoryStore.markRefreshProblem(
                accountUin,
                FriendHistoryStore.FriendHistoryRefreshStatus.STATE_FAILED,
                "解析到 ${current.size} 位好友，与服务器声明的 $total 位不一致，本轮已跳过",
            )
            return
        }
        processChunks(accountUin, chunks, current)
    }

    private fun readContacts(resp: Any?, friendCount: Int): List<ChunkContact> {
        val list = readField(resp, "vecFriendInfo", "friendInfo", "friendList") as? List<*>
            ?: return emptyList()
        return list.take(friendCount).mapNotNull { item ->
            if (item == null) return@mapNotNull null
            val uin = (readField(item, "friendUin", "uin") as? Number)?.toLong()?.toString()
                ?: return@mapNotNull null
            ChunkContact(
                uin = uin,
                nick = readField(item, "nick", "nickName") as? String ?: "",
                remark = readField(item, "remark", "remarkName") as? String ?: "",
            )
        }
    }

    private fun processChunks(
        accountUin: String,
        chunks: List<FriendListChunk>,
        current: Map<String, ChunkContact>,
    ) {
        val rawServerTime = chunks.firstOrNull { it.serverTime > 0 }?.serverTime ?: 0
        val syncTime = normalizeEpoch(rawServerTime).takeIf { it > 0 } ?: nowSec()
        val diff = applySnapshot(accountUin, current, syncTime)
        val count = diff.count
        val deleted = diff.deleted

        // 只有正在进行中的手动刷新才会被这里收尾；自然同步不写刷新状态。
        FriendHistoryStore.markRefreshDone(accountUin, count)?.let { status ->
            FriendHistoryStore.markAttempt(accountUin, status.message)
            FriendHistoryStore.diag("刷新完成：${status.message}")
        }

        if (deleted.isNotEmpty()) {
            FriendHistoryStore.diag(
                "本轮完成：total=${count} 删除=${deleted.size} " +
                    deleted.joinToString(",") { it.uin }
            )
            postDeletionNotification(deleted)
        } else {
            FriendHistoryStore.diag("本轮完成：total=${count} 删除=0")
        }
    }

    /**
     * 用一份“当前好友全量”更新快照并算出删除。
     *
     * 分片推送与好友缓存直读都走这里，保证两条取数路径的判定、记录、通知完全一致。
     *
     * @return 当前好友数（不含排除名单）与本次新增的删除记录
     */
    private fun applySnapshot(
        accountUin: String,
        current: Map<String, ChunkContact>,
        syncTime: Long,
    ): SnapshotDiff {
        val deleted = mutableListOf<FriendHistoryEvent>()
        val added = mutableListOf<String>()
        val addedEvents = mutableListOf<FriendHistoryEvent>()
        var presentCount = 0
        var suspiciousRead = false
        FriendHistoryStore.update(accountUin) { snapshot ->
            val exclusions = snapshot.exclusionSet
            val present = current.values.filter { it.uin !in exclusions }
            presentCount = present.size

            // 好友缓存刚启动时可能只返回了一部分；不足上次的一半（且上次至少 20 人）时不结算，
            // 否则会把“读取不全”误判成大批好友删了你。
            if (snapshot.initialized &&
                snapshot.lastFriendCount >= 20 &&
                presentCount * 2 < snapshot.lastFriendCount
            ) {
                suspiciousRead = true
                return@update snapshot
            }

            // 首次只建基线：此时没有“上一轮”，不能凭一次同步判断谁被删了。
            // 基线要完整包含 QQ 号、昵称、备注与头像地址。
            if (!snapshot.initialized) {
                return@update snapshot.copy(
                    accountUin = accountUin,
                    initialized = true,
                    lastSyncAt = syncTime,
                    lastFriendCount = present.size,
                    friends = present.associate { contact ->
                        contact.uin to FriendHistoryRecord(
                            uin = contact.uin,
                            nick = contact.nick,
                            remark = contact.remark,
                            avatar = contact.avatar.ifBlank { FriendSnapshotReader.avatarOf(contact.uin) },
                            seenAt = syncTime,
                            present = true,
                            status = FriendHistoryRecord.STATUS_FRIEND,
                        )
                    },
                )
            }

            val friends = snapshot.friends.toMutableMap()
            // 检测窗口起点：上一次成功同步的时间。用来把“删除”表达成区间而不是某个精确时刻。
            val previousSyncAt = snapshot.lastSyncAt
            // 新出现的好友同时补一条“新增好友”记录：历史列表立刻能看到，不用等手动添加。
            present.forEach { contact ->
                if (snapshot.friends[contact.uin]?.present != true) {
                    added += contact.uin
                    addedEvents += FriendHistoryEvent(
                        uin = contact.uin,
                        nick = contact.nick,
                        remark = contact.remark,
                        detectedAt = syncTime,
                        seenAt = syncTime,
                        source = FriendHistoryEvent.SOURCE_ADDED,
                        type = FriendHistoryEvent.TYPE_ADD,
                        timeRangeBegin = previousSyncAt,
                    )
                }
                friends[contact.uin] = FriendHistoryRecord(
                    uin = contact.uin,
                    nick = contact.nick,
                    remark = contact.remark,
                    avatar = contact.avatar.ifBlank { FriendSnapshotReader.avatarOf(contact.uin) },
                    seenAt = syncTime,
                    present = true,
                    status = FriendHistoryRecord.STATUS_FRIEND,
                )
            }

            // 排除名单里的人不出现在快照里，也就永远不会产生记录或通知。
            exclusions.forEach { friends.remove(it) }

            friends.entries.toList().forEach { (uin, record) ->
                if (uin in current) return@forEach
                if (!record.present) return@forEach
                friends[uin] = record.copy(
                    present = false,
                    deletedAt = syncTime,
                    status = FriendHistoryRecord.STATUS_DELETED,
                )
                deleted += FriendHistoryEvent(
                    uin = uin,
                    nick = record.nick,
                    remark = record.remark,
                    detectedAt = syncTime,
                    seenAt = record.seenAt,
                    source = FriendHistoryEvent.SOURCE_SYNC,
                    type = FriendHistoryEvent.TYPE_DELETE,
                    timeRangeBegin = previousSyncAt,
                )
            }

            snapshot.copy(
                accountUin = accountUin,
                initialized = true,
                lastSyncAt = syncTime,
                lastFriendCount = presentCount,
                friends = friends,
                events = (deleted + addedEvents + snapshot.events)
                    .sortedByDescending { it.detectedAt }
                    .take(FriendHistoryStore.MAX_EVENTS),
                // 本轮产生了新变动就置未读，打开记录页后清除。
                unreadSince = if (deleted.isNotEmpty() || addedEvents.isNotEmpty()) {
                    maxOf(snapshot.unreadSince, syncTime)
                } else {
                    snapshot.unreadSince
                },
            )
        }

        if (suspiciousRead) {
            FriendHistoryStore.diag("差集跳过：好友列表疑似不完整（本次 $presentCount 条）")
        } else if (addedEvents.isNotEmpty()) {
            // 全量同步也负责补“新增好友”提醒（回包路径没赶上时由这里兜底）。
            addedEvents.forEach { notifyFriendAdded(it.uin, it.nick, it.remark) }
        }
        return SnapshotDiff(presentCount, deleted, added)
    }

    /** 自己主动删好友：只写记录，不提醒（QAuxiliary 同样如此）。 */
    private fun markActiveDelete(accountUin: String, targetUin: String) {
        FriendHistoryStore.update(accountUin) { snapshot ->
            if (targetUin in snapshot.exclusionSet) return@update snapshot
            val record = snapshot.friends[targetUin] ?: return@update snapshot
            val now = nowSec()
            snapshot.copy(
                friends = snapshot.friends + (
                        targetUin to record.copy(
                            present = false,
                            deletedAt = now,
                            status = FriendHistoryRecord.STATUS_ACTIVE_DELETED,
                        )
                        ),
                events = (
                        listOf(
                            FriendHistoryEvent(
                                uin = targetUin,
                                nick = record.nick,
                                remark = record.remark,
                                detectedAt = now,
                                seenAt = record.seenAt,
                                source = FriendHistoryEvent.SOURCE_ACTIVE,
                                type = FriendHistoryEvent.TYPE_DELETE,
                            )
                        ) + snapshot.events
                        )
                    .sortedByDescending { it.detectedAt }
                    .take(FriendHistoryStore.MAX_EVENTS),
                unreadSince = maxOf(snapshot.unreadSince, now),
            )
        }
    }

    // ── 通知 ──────────────────────────────────────────────────────────────────

    /** 与 QAuxiliary 对齐：开关未写过时按“开启”处理。 */
    private fun notificationEnabled(): Boolean = runCatching {
        TCQTSetting.getValue<Boolean>(NOTIFY_KEY) ?: true
    }.getOrDefault(true)

    private fun postDeletionNotification(events: List<FriendHistoryEvent>) {
        if (!notificationEnabled()) {
            FriendHistoryStore.diag("通知跳过：开关关闭")
            return
        }
        runCatching {
            val context = HookEnv.application
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(manager)

            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                // 用户在系统设置里关掉了这个渠道，不打扰。
                FriendHistoryStore.diag("通知跳过：渠道被禁用")
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                FriendHistoryStore.diag("通知跳过：应用通知被禁用")
                return
            }

            val count = events.size
            val first = events.first()
            val firstName = "${first.displayName}(${first.uin})"
            val contentTitle = if (count > 1) "你被$count 位好友删除" else firstName
            val contentText = if (count > 1) {
                // 带上被删好友的 QQ 号（最多列 5 个，其余用“等”收尾）。
                val names = events.take(5).joinToString("、") { "${it.displayName}(${it.uin})" }
                "$names 等 $count 位好友删除了你"
            } else if (first.seenAt > 0) {
                "在约 ${relativeTime(first.seenAt)} 删除了你"
            } else {
                "检测到对方已把你删除"
            }

            val contentIntent = PendingIntent.getActivity(
                context,
                NOTIFY_ID,
                Intent().apply {
                    setClassName(HookEnv.hostAppPackageName, ModuleComponents.FRIEND_HISTORY_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setTicker("检测到 $count 位好友删除了你")
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setAutoCancel(true)
                // 不要设 setOnlyAlertOnce：同一 id 的后续更新会被系统静默处理，
                // 表现为“只有第一次有提醒”（r728 已踩过一次）。
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(contentIntent)
                .build()

            manager.notify(NOTIFY_ID, notification)
            FriendHistoryStore.diag("通知已发送：count=$count id=$NOTIFY_ID")
        }.onFailure {
            Log.e("好友历史：发送被删通知失败", it)
            FriendHistoryStore.diag("通知失败：${it.message}")
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "好友列表同步后发现好友删除你时提醒"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 200, 100)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /** 新增好友提醒：与「被删好友通知」同一套权限/渠道检查，但渠道、id、文案完全独立。 */
    private fun addedNotificationEnabled(): Boolean = runCatching {
        TCQTSetting.getValue<Boolean>(NEW_FRIEND_NOTIFY_KEY) ?: true
    }.getOrDefault(true)

    /**
     * 发一条「新增好友」提醒。
     *
     * 调用方必须已经确认**真正写入了**一条新的新增记录（好友本来就在快照里时不调用）；
     * 另外这里再按 QQ 号做 10 分钟去重，避免加好友回包与全量同步同时命中同一人时发两条。
     */
    private fun notifyFriendAdded(uin: String, nick: String, remark: String) {
        if (!addedNotificationEnabled()) {
            FriendHistoryStore.diag("新增通知跳过：开关关闭（$uin）")
            return
        }
        val now = System.currentTimeMillis()
        val last = addedNotifiedAt[uin]
        if (last != null && now - last < ADDED_NOTIFY_DEDUP_MS) {
            FriendHistoryStore.diag("新增通知跳过：$uin 刚刚已提醒")
            return
        }
        addedNotifiedAt[uin] = now

        runCatching {
            val context = HookEnv.application
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureAddedChannel(manager)

            val channel = manager.getNotificationChannel(ADDED_CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                FriendHistoryStore.diag("新增通知跳过：渠道被禁用")
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                FriendHistoryStore.diag("新增通知跳过：应用通知被禁用")
                return
            }

            // 显示名：备注优先，其次昵称，最后 QQ 号（与历史列表口径一致）。
            val displayName = remark.ifBlank { nick }.ifBlank { uin }
            val id = ADDED_NOTIFY_ID_BASE + (uin.hashCode() and 0xFF)
            val contentIntent = PendingIntent.getActivity(
                context,
                id,
                Intent().apply {
                    setClassName(HookEnv.hostAppPackageName, ModuleComponents.FRIEND_HISTORY_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, ADDED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setTicker("新增好友：$displayName")
                .setContentTitle("新增好友 $displayName")
                .setContentText("QQ $uin · 已记入好友变动记录")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("QQ $uin · 已记入好友变动记录")
                )
                .setAutoCancel(true)
                // 同一个人的重复提醒已被上面的去重挡掉，这里再兜一层：更新同 id 不重复响铃。
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(ADDED_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .build()

            manager.notify(id, notification)
            FriendHistoryStore.diag("新增通知已发送：$uin（名字=$displayName）")
        }.onFailure {
            Log.e("好友历史：发送新增通知失败", it)
            FriendHistoryStore.diag("新增通知失败：${it.message}")
        }
    }

    private fun ensureAddedChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(ADDED_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            ADDED_CHANNEL_ID,
            ADDED_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "好友真正写入好友变动记录后提醒"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 200, 100)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    // ── 主动刷新（可选增强）────────────────────────────────────────────────────

    internal data class RefreshOutcome(val ok: Boolean, val reason: String)

    /**
     * 设置页按钮：手动刷新一次（**强制读明细并比对**，不受人数没变就跳过的影响）。
     *
     * 先读人数确认取数路径通不通，再读明细做集合比对；人数接口失败时把原文带回界面。
     *
     * 整体包一层 runCatching：任何异常都必须把盘上的「进行中」收尾，
     * 否则界面会永远停在「刷新中」、下次点击也只会得到「正在刷新中」。
     */
    fun manualRefresh(): String = runCatching { doManualRefresh() }
        .getOrElse { error ->
            val message = "刷新异常：${error.message ?: error.javaClass.simpleName}"
            val uin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
            if (uin.isNotBlank()) {
                FriendHistoryStore.markRefreshProblem(
                    uin,
                    FriendHistoryStore.FriendHistoryRefreshStatus.STATE_FAILED,
                    message,
                )
                FriendHistoryStore.recordCountCheck(uin, message)
                FriendHistoryStore.diag(message)
            }
            Log.e("好友历史：手动刷新异常", error)
            message
        }

    private fun doManualRefresh(): String {
        val accountUin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (accountUin.isBlank()) return "QQ 未登录，无法刷新好友列表"

        val current = FriendHistoryStore.readRefreshStatus()
        if (current.accountUin == accountUin && current.active) {
            val age = nowSec() - current.startedAt
            if (age in 0..STALE_REFRESH_SEC) {
                return "正在刷新中：${refreshStatusText(current)}"
            }
            // 过期说明上次没走完（进程被杀/调用卡死），忽略旧标记直接重新开始。
            FriendHistoryStore.diag("刷新状态过期（${age}s），忽略旧进行中标记")
        }

        FriendHistoryStore.markRefreshStarted(accountUin, "正在读取好友人数…")

        val count = FriendSnapshotReader.readCount()
        val countText = if (count.ok) {
            "人数 ${count.count}（${count.source}）"
        } else {
            "人数读取失败：${count.detail}"
        }
        FriendHistoryStore.diag("手动刷新：$countText")

        val details = FriendSnapshotReader.readDetails()
        if (!details.ok) {
            val message = "取数路径不可用：$countText；明细读取失败：${details.detail}"
            FriendHistoryStore.markRefreshProblem(
                accountUin,
                FriendHistoryStore.FriendHistoryRefreshStatus.STATE_FAILED,
                message,
            )
            FriendHistoryStore.markAttempt(accountUin, message)
            FriendHistoryStore.recordCountCheck(accountUin, message)
            FriendHistoryStore.diag("手动刷新失败：$message")
            return message
        }

        val message = settleSnapshot(accountUin, details, countText, force = true)
        FriendHistoryStore.markRefreshDone(accountUin, details.friends.size, message)
        return message
    }

    private fun checkFriendCount(label: String): Boolean {
        // 兜底：检测会读宿主好友缓存、读写快照 JSON，绝不能在主线程跑。
        // 将来任何新调用点误落在主线程上，这里直接改投 IO，不让它传染给首屏。
        if (ModuleScope.isMainThread) {
            ModuleScope.launchIO("好友历史检测 $label") { runCatching { checkFriendCount(label) } }
            return false
        }
        // 回前台、10 分钟周期、登录重试可能撞在一起：同一时间只允许一个在跑。
        if (!countCheckRunning.compareAndSet(false, true)) {
            FriendHistoryStore.diag("$label：已有检测在执行，跳过")
            return false
        }
        return try {
            checkFriendCountLocked(label)
        } finally {
            countCheckRunning.set(false)
        }
    }

    /**
     * [checkFriendCount] 的实际实现，调用方保证 [countCheckRunning] 已持有。
     *
     * @return true 表示这次没读到人数、需要稍后重试
     */
    private fun checkFriendCountLocked(label: String): Boolean {
        val accountUin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (accountUin.isBlank()) return true

        val count = FriendSnapshotReader.readCount()
        if (!count.ok) {
            val message = "人数读取失败：${count.detail}"
            FriendHistoryStore.recordCountCheck(accountUin, message)
            FriendHistoryStore.diag("$label：$message")
            return true
        }

        val snapshot = FriendHistoryStore.load(accountUin)
        val countText = "人数 ${count.count}（${count.source}）"

        if (!snapshot.initialized) {
            val details = FriendSnapshotReader.readDetails()
            if (!details.ok) {
                val message = "$countText，但首次基线需要明细：${details.detail}"
                FriendHistoryStore.recordCountCheck(accountUin, message)
                FriendHistoryStore.diag("$label：$message")
                return true
            }
            val message = settleSnapshot(accountUin, details, countText, force = false)
            FriendHistoryStore.recordCountCheck(accountUin, "$label：$message")
            return false
        }

        val countSame = count.count == snapshot.lastFriendCount
        val staleSameCount = countSame && (
            snapshot.lastSyncAt <= 0 ||
                nowSec() - snapshot.lastSyncAt >= FORCE_DETAIL_INTERVAL_SEC
            )
        if (countSame && !staleSameCount) {
            val message = "$countText，与上次一致，跳过明细"
            FriendHistoryStore.recordCountCheck(accountUin, message)
            FriendHistoryStore.diag("$label：$message")
            return false
        }
        if (staleSameCount) {
            FriendHistoryStore.diag("$label：人数一致但距上次全量超过 ${FORCE_DETAIL_INTERVAL_SEC / 60} 分钟，强制读明细")
        }

        val details = FriendSnapshotReader.readDetails()
        if (!details.ok) {
            val message = if (countSame) {
                "$countText，人数一致但需要全量核对，明细读取失败：${details.detail}"
            } else {
                "$countText，与上次 ${snapshot.lastFriendCount} 不同，但明细读取失败：${details.detail}"
            }
            FriendHistoryStore.recordCountCheck(accountUin, message)
            FriendHistoryStore.diag("$label：$message")
            return true
        }
        val message = settleSnapshot(accountUin, details, countText, force = staleSameCount)
        FriendHistoryStore.recordCountCheck(accountUin, "$label：$message")
        return false
    }

    /**
     * 用直读到的明细结算：写基线/差集、写记录、发通知，并返回一句可显示的结果。
     *
     * [force] 为 true 时即使人数没变也重新结算（手动刷新场景）。
     */
    private fun settleSnapshot(
        accountUin: String,
        read: FriendSnapshotReader.Result,
        countText: String,
        force: Boolean,
    ): String {
        val contacts = read.friends.associate {
            it.uin to ChunkContact(it.uin, it.nick, it.remark, it.avatar)
        }
        val before = FriendHistoryStore.load(accountUin)
        if (!force && before.initialized && before.lastFriendCount == contacts.size) {
            return "$countText，与上次一致，跳过明细"
        }

        val diff = applySnapshot(accountUin, contacts, nowSec())
        val addedText = if (diff.added.isEmpty()) {
            "新增 0"
        } else {
            "新增 ${diff.added.size}（${diff.added.take(5).joinToString(",")}）"
        }
        val deletedText = if (diff.deleted.isEmpty()) {
            "被删 0"
        } else {
            "被删 ${diff.deleted.size}（${diff.deleted.take(5).joinToString(",") { it.uin }}）"
        }
        val message = "${read.source}：$countText，$addedText，$deletedText"

        FriendHistoryStore.markAttempt(accountUin, message)
        FriendHistoryStore.diag("直读结算：$message；raw=${read.detail}")
        if (diff.deleted.isNotEmpty()) {
            postDeletionNotification(diff.deleted)
        }
        return message
    }

    internal data class RefreshProgress(
        val active: Boolean,
        val finished: Boolean,
        val message: String,
    )

    /** 界面轮询用：读取落盘的刷新进度，跨进程可见。 */
    fun refreshProgress(): RefreshProgress {
        val accountUin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        val status = FriendHistoryStore.readRefreshStatus()
        if (accountUin.isBlank() || status.accountUin != accountUin) {
            return RefreshProgress(false, false, "没有进行中的刷新")
        }
        return RefreshProgress(status.active, status.finished, refreshStatusText(status))
    }

    private fun refreshStatusText(status: FriendHistoryStore.FriendHistoryRefreshStatus): String =
        when (status.state) {
            FriendHistoryStore.FriendHistoryRefreshStatus.STATE_REQUESTED ->
                "已发起，等待服务器回包"
            FriendHistoryStore.FriendHistoryRefreshStatus.STATE_RECEIVING ->
                "接收中：已收 ${status.chunks} 个分片 / ${status.parsedFriends} 位好友" +
                        if (status.total > 0) "（服务器声明 ${status.total} 位）" else ""
            FriendHistoryStore.FriendHistoryRefreshStatus.STATE_DONE ->
                status.message.ifBlank { "已完成" }
            FriendHistoryStore.FriendHistoryRefreshStatus.STATE_TIMEOUT,
            FriendHistoryStore.FriendHistoryRefreshStatus.STATE_FAILED ->
                status.message.ifBlank { "未完成" }
            else -> "空闲"
        }

    /**
     * 反射拿 `FriendListHandler` 并调用刷新。
     *
     * 不能直接写 `appRuntime.getBusinessHandler(...)`：宿主里这个方法名可能被混淆成 `a`，
     * 直接虚调用会抛 NoSuchMethodError，再被 runCatching 吞掉，表现为“刷新永远失败且无感”。
     * 这里两种名字都试，方法本身也按签名而不是名字查找。
     */
    fun requestRefresh(): RefreshOutcome {
        val runtime = runCatching { QQInterfaces.appRuntime }.getOrNull()
            ?: return RefreshOutcome(false, "宿主 AppRuntime 未就绪")
        val handler = resolveFriendListHandler(runtime)
            ?: return RefreshOutcome(false, "找不到 FriendListHandler（getBusinessHandler/a 均不可用）")
        val (method, label) = refreshMethod(handler)
            ?: return RefreshOutcome(false, "FriendListHandler 没有可用的刷新方法")
        val args = method.parameterTypes.map { type ->
            if (type == Int::class.javaPrimitiveType) 1 as Any else true as Any
        }
        return runCatching {
            method.isAccessible = true
            method.invoke(handler, *args.toTypedArray())
            RefreshOutcome(true, "已发起刷新（$label）")
        }.getOrElse { error ->
            Log.e("好友历史：主动刷新失败", error)
            RefreshOutcome(false, "刷新调用失败：${error.cause?.message ?: error.message}")
        }
    }

    private fun resolveFriendListHandler(runtime: Any): Any? {
        val baseHandlerClass = load("com.tencent.mobileqq.app.BusinessHandler")
            ?: runCatching { load(FRIEND_HANDLER_NAME)?.superclass }.getOrNull()
        val methods = runtime.javaClass.methods

        methods.firstOrNull { method ->
            method.name == "getBusinessHandler" && method.parameterTypes.size == 1 &&
                (method.parameterTypes[0] == String::class.java ||
                    method.parameterTypes[0] == Int::class.javaPrimitiveType)
        }?.let { method ->
            val arg: Any =
                if (method.parameterTypes[0] == String::class.java) FRIEND_HANDLER_NAME else 1
            return runCatching { method.invoke(runtime, arg) }.getOrNull()
        }

        if (baseHandlerClass != null) {
            methods.firstOrNull { method ->
                method.name == "a" && method.returnType == baseHandlerClass &&
                    method.parameterTypes.size == 1 &&
                    (method.parameterTypes[0] == String::class.java ||
                        method.parameterTypes[0] == Int::class.javaPrimitiveType)
            }?.let { method ->
                val arg: Any =
                    if (method.parameterTypes[0] == String::class.java) FRIEND_HANDLER_NAME else 1
                return runCatching { method.invoke(runtime, arg) }.getOrNull()
            }
        }
        return null
    }

    private fun refreshMethod(handler: Any): Pair<java.lang.reflect.Method, String>? {
        val candidates = handler.javaClass.methods.filter {
            it.returnType == Void.TYPE &&
                !java.lang.reflect.Modifier.isStatic(it.modifiers)
        }
        for ((label, params) in REFRESH_SIGNATURES) {
            val matched = candidates.filter { it.parameterTypes.contentEquals(params) }
            if (matched.isEmpty()) continue
            val preferred = matched.firstOrNull { method ->
                val name = method.name.lowercase()
                name.contains("refresh") || name.contains("friendlist") || name.contains("getfriend")
            } ?: matched.first()
            return preferred to label
        }
        return null
    }

    private val REFRESH_SIGNATURES: List<Pair<String, Array<Class<*>>>> = listOf(
        "boolean,boolean" to arrayOf<Class<*>>(
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ),
        "boolean" to arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!),
        "boolean,int" to arrayOf<Class<*>>(
            Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
        ),
        "int" to arrayOf<Class<*>>(Int::class.javaPrimitiveType!!),
    )

    /** 宿主新增好友回包；类不存在或版本改名时自动跳过，由人数兜底接管。 */
    private val ADD_FRIEND_RESP_CLASSES = listOf(
        "friendlist.AddFriendResp",
        "friendlist.AgreeAddFriendResp",
    )

    // ── 反射工具 ─────────────────────────────────────────────────────────────────

    /**
     * 读字段：逐层查 declared 字段并尝试多个候选名。
     *
     * 旧实现只用 `Class.getField`（仅 public），且严格按单一名字匹配；
     * 一旦宿主把字段名混淆/挪到父类，就整条链路静默失效。这里全部覆盖。
     */
    private fun readField(target: Any?, vararg names: String): Any? {
        if (target == null) return null
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            val current = clazz
            for (name in names) {
                val value = runCatching {
                    val field = current.getDeclaredField(name)
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
                if (value != null) return value
            }
            clazz = current.superclass
        }
        return null
    }

    /** 上一次在后台算好的状态文本；组合阶段只读这里，不碰文件。 */
    @Volatile
    private var statusCache = "检测：读取中…"

    @Volatile
    private var statusRefreshAt = 0L

    /**
     * 设置页自证信息：不登录也能看到 hook 是否挂上；登录后能看到同步/好友数/刷新结果。
     *
     * 本方法在 Compose 组合阶段被调用（主线程），因此**只返回缓存**：
     * 好友快照 JSON 可能有几百 KB，跨进程文件锁也可能被占用，在组合里读文件
     * 等于让设置页开局就卡一下。真正的读取放到后台，算完后再自然重组。
     */
    fun statusText(): String {
        val now = SystemClock.elapsedRealtime()
        if (now - statusRefreshAt >= STATUS_CACHE_MS) {
            statusRefreshAt = now
            ModuleScope.launchIO("好友历史状态") {
                runCatching { buildStatusText() }
                    .onSuccess { statusCache = it }
                    .onFailure { Log.w("好友历史：生成状态文本失败（${it.message}）") }
            }
        }
        return statusCache
    }

    private fun buildStatusText(): String {
        val hook = FriendHistoryStore.readHookStatus()
        val lines = mutableListOf<String>()
        lines += "检测：主进程 ${hook.main.ifBlank { "未安装" }}；:MSF ${hook.msf.ifBlank { "未安装" }}"
        val uin = runCatching { QQInterfaces.currentUin }.getOrDefault("")
        if (uin.isBlank()) {
            lines += "账号：未登录（登录后显示同步状态）"
            return lines.joinToString("\n")
        }
        val snapshot = FriendHistoryStore.load(uin)
        val sync = if (snapshot.lastSyncAt > 0) {
            FriendHistoryStore.relativeTime(snapshot.lastSyncAt)
        } else {
            "从未"
        }
        lines += "最近全量同步：$sync（好友 ${snapshot.lastFriendCount} 个）"
        // 自证基线内容：自动缓存确实写入了 QQ 号/昵称/头像地址，而不是只有人数。
        val present = snapshot.friends.values.count { it.present }
        val withNick = snapshot.friends.values.count { it.present && it.nick.isNotBlank() }
        val withAvatar = snapshot.friends.values.count { it.present && it.avatar.isNotBlank() }
        lines += "基线明细：$present 条（昵称 $withNick，头像地址 $withAvatar）"
        val attempt = if (snapshot.lastAttemptAt > 0) {
            "${FriendHistoryStore.relativeTime(snapshot.lastAttemptAt)}（${snapshot.lastAttemptResult}）"
        } else {
            "从未"
        }
        lines += "上次主动刷新：$attempt"
        val countCheck = if (snapshot.lastCountCheckAt > 0) {
            "${FriendHistoryStore.relativeTime(snapshot.lastCountCheckAt)}（${snapshot.lastCountCheckResult}）"
        } else {
            "从未（登录后自动检测）"
        }
        lines += "上次登录人数检测：$countCheck"
        val refresh = FriendHistoryStore.readRefreshStatus()
        if (refresh.accountUin == uin &&
            refresh.state != FriendHistoryStore.FriendHistoryRefreshStatus.STATE_IDLE
        ) {
            lines += "刷新状态：${refreshStatusText(refresh)}"
        }
        val latest = snapshot.events.maxOfOrNull { it.detectedAt } ?: 0
        lines += "最近删除记录：${if (latest > 0) FriendHistoryStore.relativeTime(latest) else "暂无"}"
        return lines.joinToString("\n")
    }

    internal data class ManualNickLookup(
        val nick: String = "",
        val remark: String = "",
        val source: String = "",
    ) {
        val displayName: String get() = remark.ifBlank { nick }
    }

    /**
     * 手动添加记录时按 QQ 号查昵称/备注。
     *
     * 顺序：本地好友基线（已删好友也能查到）→ 历史记录 → 宿主好友缓存。
     * 都查不到时返回空结果，由界面提示但**不阻塞添加**。
     */
    fun lookupManualNickname(accountUin: String, targetUin: String): ManualNickLookup {
        val snapshot = runCatching { FriendHistoryStore.load(accountUin) }.getOrNull()
        snapshot?.friends?.get(targetUin)?.let { record ->
            if (record.nick.isNotBlank() || record.remark.isNotBlank()) {
                return ManualNickLookup(record.nick, record.remark, "好友基线")
            }
        }
        snapshot?.events?.firstOrNull {
            it.uin == targetUin && (it.nick.isNotBlank() || it.remark.isNotBlank())
        }?.let { event ->
            return ManualNickLookup(event.nick, event.remark, "历史记录")
        }
        val entry = runCatching { FriendSnapshotReader.lookupDetail(targetUin) }.getOrNull()
        if (entry != null && (entry.nick.isNotBlank() || entry.remark.isNotBlank())) {
            return ManualNickLookup(entry.nick, entry.remark, "好友缓存")
        }
        return ManualNickLookup()
    }

    private fun normalizeEpoch(value: Long): Long =
        if (value > 10_000_000_000L) value / 1000 else value

    private fun relativeTime(epochSec: Long): String = FriendHistoryStore.relativeTime(epochSec)

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
}

/**
 * 主动读取宿主的好友缓存（**不依赖服务器推送**）。
 *
 * 与群打卡读取群列表完全同一模式：通过 `QRoute.api(...)` 拿到宿主缓存仓库接口，
 * 同步调用它的方法，返回结构化数据，不需要协议回调。
 *
 * 分两种读取粒度：
 * - [readCount]：只要人数，优先 `IFriendsInfoService.getFriendCount("")`，最轻量；
 * - [readDetails]：要 QQ 号 / 昵称 / 备注，优先 `IFriendsInfoService.getAllFriend("")`。
 *
 * 两条宿主实现都会尝试：NT 好友信息仓库（QFun 同款）与老式 `FriendsManager`
 * （XAutoDaily 同款）。都取不到时 [CountResult.detail] / [Result.detail] 会写清楚
 * 卡在哪个类、哪个方法、什么异常，界面据此显示“取数路径不可用”而不是笼统超时。
 */
internal object FriendSnapshotReader {

    internal data class Entry(
        val uin: String,
        val nick: String,
        val remark: String,
        val avatar: String = avatarOf(uin),
    )

    internal data class Result(
        val ok: Boolean,
        val source: String = "",
        val friends: List<Entry> = emptyList(),
        val detail: String = "",
    )

    internal data class CountResult(
        val ok: Boolean,
        val count: Int = 0,
        val source: String = "",
        val detail: String = "",
    )

    private const val FRIENDS_INFO_API = "com.tencent.qqnt.ntrelation.friendsinfo.api.IFriendsInfoService"
    private const val QQ_MANAGER_FACTORY = "com.tencent.mobileqq.app.QQManagerFactory"

    private val COUNT_METHODS = listOf(
        "getFriendCount",
        "getFriendsCount",
        "getAllFriendCount",
        "getBuddyCount",
        "getFriendListCount",
    )
    private val LIST_METHODS = listOf(
        "getAllFriend",
        "getAllFriends",
        "getAllFriendList",
        "getAllFriendSimpleInfo",
        "getAllFriendInfo",
        "getFriends",
        "getFriendList",
    )
    private val UIN_NAMES = arrayOf("uin", "friendUin", "friendUinStr")
    private val NICK_NAMES = arrayOf("nick", "friendNick", "friendName", "nickName", "name", "remark")
    private val REMARK_NAMES = arrayOf("remark", "remarkName", "remarkNick")
    private val UIN_REGEX = Regex("^\\d{5,12}$")

    /** QQ 号头像地址；宿主不直接给头像时用它，保证基线里能持久化“头像地址”。 */
    fun avatarOf(uin: String): String = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=640"

    /** 只读人数：先走 count 接口，再退化为“列表长度”。 */
    fun readCount(): CountResult {
        val failures = mutableListOf<String>()
        friendsInfoApi(failures)?.let { api ->
            countFromApi(api, failures)?.let { return it }
            countFromListApi(api, failures)?.let { return it }
        }
        managerCount(failures)?.let { return it }
        return CountResult(
            ok = false,
            detail = failures.joinToString("；").ifBlank { "宿主没有可用的人数接口" },
        )
    }

    fun readDetails(): Result {
        val failures = mutableListOf<String>()
        readFromFriendsInfoService(failures)?.let { return it }
        readFromFriendsManager(failures)?.let { return it }
        return Result(
            ok = false,
            detail = failures.joinToString("；").ifBlank { "宿主没有可用的好友缓存接口" },
        )
    }

    /** 兼容旧调用点。 */
    fun readCurrent(): Result = readDetails()

    /**
     * 按单个 QQ 号查昵称/备注，供“手动添加记录”自动回填。
     *
     * 先走好友信息仓库的单人接口（免全量遍历）；老版本没有该接口时退回 [readDetails]，
     * 从当前好友缓存里找。两处都找不到（陌生人/已删好友且本地无快照）返回 null。
     */
    fun lookupDetail(uin: String): Entry? {
        if (!UIN_REGEX.matches(uin)) return null
        val failures = mutableListOf<String>()
        friendsInfoApi(failures)?.let { api ->
            lookupFromApi(api, uin)?.let { return it }
        }
        return readDetails().takeIf { it.ok }?.friends?.firstOrNull { it.uin == uin }
    }

    private fun lookupFromApi(api: Any, uin: String): Entry? {
        val methods = apiMethods(api)
        val uid = methods.firstOrNull {
            it.name == "getUidFromUin" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        }?.let { method ->
            runCatching {
                method.isAccessible = true
                method.invoke(api, uin) as? String
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }

        val candidates = methods.filter { method ->
            method.parameterCount == 2 &&
                method.parameterTypes.all { it == String::class.java } &&
                method.returnType != Void.TYPE &&
                (method.name.contains("SimpleInfo", ignoreCase = true) ||
                    method.name.contains("FriendInfo", ignoreCase = true))
        }
        for (method in candidates) {
            val first = if (method.name.contains("Uid", ignoreCase = true) && uid != null) uid else uin
            val raw = runCatching {
                method.isAccessible = true
                method.invoke(api, first, "")
            }.getOrNull() ?: continue
            toEntry(raw)?.takeIf { it.uin == uin }?.let { return it }
        }
        return null
    }

    // ── 路径一：NT 好友信息仓库（QRoute 缓存接口）───────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun friendsInfoApi(failures: MutableList<String>): Any? {
        val apiClass = load(FRIENDS_INFO_API) ?: run {
            failures += "缺失类 IFriendsInfoService"
            return null
        }
        // 两种重载都试：不同版本 QRoute.api 的签名不一样。
        runCatching { QRoute.api(apiClass as Class<QRouteApi>) }.getOrNull()?.let { return it }
        runCatching {
            val method = QRoute::class.java.getMethod("api", Class::class.java, Boolean::class.javaPrimitiveType)
            method.invoke(null, apiClass, true)
        }.getOrNull()?.let { return it }
        failures += "IFriendsInfoService 实例获取失败（QRoute.api 不支持该接口）"
        return null
    }

    private fun apiMethods(api: Any): List<java.lang.reflect.Method> {
        val declared = api.javaClass.methods.toList()
        val iface = api.javaClass.interfaces.flatMap { it.methods.toList() }
        return (declared + iface).distinctBy { it.name + it.parameterTypes.joinToString { p -> p.name } }
    }

    private fun countFromApi(api: Any, failures: MutableList<String>): CountResult? {
        val methods = apiMethods(api).filter { method ->
            method.name in COUNT_METHODS &&
                (method.returnType == Int::class.javaPrimitiveType ||
                    method.returnType == Long::class.javaPrimitiveType ||
                    method.returnType == Integer::class.java)
        }
        if (methods.isEmpty()) {
            failures += "IFriendsInfoService 没有人数方法（候选：${COUNT_METHODS.joinToString("/")}）"
            return null
        }
        for (method in methods) {
            val args = Array<Any?>(method.parameterCount) { "" }
            val raw = runCatching {
                method.isAccessible = true
                method.invoke(api, *args)
            }.getOrElse {
                failures += "${method.name} 调用失败：${short(it)}"
                continue
            }
            val count = (raw as? Number)?.toInt() ?: -1
            if (count > 0) {
                return CountResult(true, count, "人数接口(${method.name})", "count=$count")
            }
            failures += "${method.name} 返回 $count（缓存未就绪或未登录）"
        }
        return null
    }

    private fun countFromListApi(api: Any, failures: MutableList<String>): CountResult? {
        val method = apiMethods(api).firstOrNull { it.name in LIST_METHODS && it.parameterCount == 1 } ?: return null
        val raw = runCatching {
            method.isAccessible = true
            method.invoke(api, "")
        }.getOrNull() as? Iterable<*> ?: return null
        val count = raw.count { it != null }
        return if (count > 0) {
            CountResult(true, count, "列表长度(${method.name})", "items=$count")
        } else {
            failures += "${method.name} 返回空列表"
            null
        }
    }

    private fun readFromFriendsInfoService(failures: MutableList<String>): Result? {
        val api = friendsInfoApi(failures) ?: return null
        val method = apiMethods(api).firstOrNull { candidate ->
            candidate.name in LIST_METHODS &&
                Iterable::class.java.isAssignableFrom(candidate.returnType) &&
                candidate.parameterCount <= 1
        }
        if (method == null) {
            failures += "IFriendsInfoService 没有取好友列表的方法（候选：${LIST_METHODS.joinToString("/")}）"
            return null
        }

        val raw = runCatching {
            method.isAccessible = true
            if (method.parameterCount == 1) method.invoke(api, "") else method.invoke(api)
        }.getOrElse {
            failures += "${method.name} 调用失败：${short(it)}"
            return null
        }
        val items = (raw as? Iterable<*>)?.filterNotNull()
        if (items == null) {
            failures += "${method.name} 返回 ${raw?.javaClass?.name ?: "null"}，不是列表"
            return null
        }
        val entries = items.mapNotNull { toEntry(it) }
        if (entries.isEmpty()) {
            failures += "${method.name} 返回 ${items.size} 条，但解析不出 QQ 号"
            return null
        }
        return Result(
            ok = true,
            source = "好友缓存(${method.name})",
            friends = entries.distinctBy { it.uin },
            detail = "raw=${items.size}",
        )
    }

    // ── 路径二：老式 FriendsManager（XAutoDaily 同款）────────────────────────────

    private fun readFromFriendsManager(failures: MutableList<String>): Result? {
        val runtime = runCatching { QQInterfaces.appRuntime }.getOrNull()
        if (runtime == null) {
            failures += "AppRuntime 未就绪"
            return null
        }
        val ids = managerIds()
        for (id in ids) {
            val manager = runCatching { runtime.getManager(id) }.getOrNull() ?: continue
            readFriendMap(manager)?.let { return it }
            readFriendList(manager)?.let { return it }
        }
        failures += "好友管理器(${ids.joinToString("/")}) 没有可读的 Map/List 接口"
        return null
    }

    private fun managerCount(failures: MutableList<String>): CountResult? {
        val runtime = runCatching { QQInterfaces.appRuntime }.getOrNull()
        if (runtime == null) {
            failures += "AppRuntime 未就绪"
            return null
        }
        val ids = managerIds()
        for (id in ids) {
            val manager = runCatching { runtime.getManager(id) }.getOrNull() ?: continue
            countFromFriendMap(manager)?.let { return it }
            countFromFriendList(manager)?.let { return it }
        }
        failures += "好友管理器(${ids.joinToString("/")}) 没有可读的人数/列表接口"
        return null
    }

    private fun countFromFriendMap(manager: Any): CountResult? {
        for (method in manager.javaClass.declaredMethods) {
            if (method.parameterCount != 1) continue
            if (method.parameterTypes[0] != Boolean::class.javaPrimitiveType) continue
            if (!Map::class.java.isAssignableFrom(method.returnType)) continue
            val raw = runCatching {
                method.isAccessible = true
                method.invoke(manager, true)
            }.getOrNull() as? Map<*, *> ?: continue
            val count = raw.values.filterNotNull().sumOf { value ->
                (value as? Iterable<*>)?.count { it != null } ?: 0
            }
            if (count > 0) {
                return CountResult(true, count, "好友管理器(${method.name})", "groups=${raw.size}")
            }
        }
        return null
    }

    private fun countFromFriendList(manager: Any): CountResult? {
        for (method in manager.javaClass.methods) {
            if (method.parameterCount != 0) continue
            if (!List::class.java.isAssignableFrom(method.returnType)) continue
            if (!method.name.contains("friend", ignoreCase = true)) continue
            val raw = runCatching { method.invoke(manager) }.getOrNull() as? List<*> ?: continue
            val count = raw.count { it != null }
            if (count > 0) {
                return CountResult(true, count, "好友管理器(${method.name})", "items=$count")
            }
        }
        return null
    }

    private fun managerIds(): List<Int> {
        val ids = mutableListOf<Int>()
        runCatching {
            val factory = load(QQ_MANAGER_FACTORY) ?: return@runCatching
            val field = factory.getDeclaredField("FRIENDS_MANAGER").apply { isAccessible = true }
            (field.get(null) as? Number)?.toInt()?.let(ids::add)
        }
        // 旧版本没有常量时按已知值兜底。
        ids += listOf(50, 51)
        return ids.distinct()
    }

    /** `FriendsManager` 的分组 Map：private `Map<分组名, List<Friends>> getFriendMap(boolean)`。 */
    private fun readFriendMap(manager: Any): Result? {
        for (method in manager.javaClass.declaredMethods) {
            if (method.parameterCount != 1) continue
            if (method.parameterTypes[0] != Boolean::class.javaPrimitiveType) continue
            if (!Map::class.java.isAssignableFrom(method.returnType)) continue
            val raw = runCatching {
                method.isAccessible = true
                method.invoke(manager, true)
            }.getOrNull() as? Map<*, *> ?: continue
            val entries = raw.values.filterNotNull()
                .flatMap { value ->
                    (value as? Iterable<*>)?.filterNotNull()?.mapNotNull { toEntry(it) } ?: emptyList()
                }
            if (entries.isNotEmpty()) {
                return Result(
                    ok = true,
                    source = "好友管理器(${method.name})",
                    friends = entries.distinctBy { it.uin },
                    detail = "groups=${raw.size}",
                )
            }
        }
        return null
    }

    /** 老版本 `FriendsManager` 的 public `List<Friends> getFriendList()`。 */
    private fun readFriendList(manager: Any): Result? {
        for (method in manager.javaClass.methods) {
            if (method.parameterCount != 0) continue
            if (!List::class.java.isAssignableFrom(method.returnType)) continue
            if (!method.name.contains("friend", ignoreCase = true)) continue
            val raw = runCatching { method.invoke(manager) }.getOrNull() as? List<*> ?: continue
            val entries = raw.filterNotNull().mapNotNull { toEntry(it) }
            if (entries.isNotEmpty()) {
                return Result(
                    ok = true,
                    source = "好友管理器(${method.name})",
                    friends = entries.distinctBy { it.uin },
                    detail = "items=${raw.size}",
                )
            }
        }
        return null
    }

    // ── 字段读取 ───────────────────────────────────────────────────────────────

    private fun toEntry(item: Any): Entry? {
        val uin = readValue(item, UIN_NAMES)?.let(::asUinString) ?: return null
        if (!UIN_REGEX.matches(uin)) return null
        return Entry(
            uin = uin,
            nick = readValue(item, NICK_NAMES) as? String ?: "",
            remark = readValue(item, REMARK_NAMES) as? String ?: "",
        )
    }

    private fun asUinString(value: Any): String? = when (value) {
        is Number -> value.toLong().toString()
        is String -> value.trim().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun readValue(target: Any, names: Array<String>): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val current = clazz
            for (name in names) {
                val method = runCatching {
                    current.getDeclaredMethod(name).apply { isAccessible = true }
                }.getOrNull()
                if (method != null && method.parameterCount == 0) {
                    val value = runCatching { method.invoke(target) }.getOrNull()
                    if (value != null) return value
                }
                val field = runCatching {
                    current.getDeclaredField(name).apply { isAccessible = true }
                }.getOrNull()
                if (field != null) {
                    val value = runCatching { field.get(target) }.getOrNull()
                    if (value != null) return value
                }
            }
            clazz = current.superclass
        }
        return null
    }

    private fun short(error: Throwable): String =
        error.cause?.message ?: error.message ?: error.javaClass.simpleName
}
