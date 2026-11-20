package com.owo233.tcqt.core.diag

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.owo233.tcqt.core.action.StartupTrace
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主线程卡顿看门狗。
 *
 * 原理：每秒向主线程 Looper 投递一次心跳，看门狗线程每秒检查"距离上次心跳被执行过了多久"。
 * 主线程被堵住时心跳无法执行，差值超过阈值就取证一次；心跳恢复后重新计时，所以**同一段
 * 卡顿只记一次**。
 *
 * 开销：主线程侧只有一次空 Runnable 执行 + 一次 `postDelayed`；看门狗线程每秒一次 sleep 醒来
 * 比较两个 long。都不涉及堆栈采集，正常运行时占用可忽略。堆栈只在判定卡顿时抓一次。
 *
 * 主线程堆栈用 `Thread.stackTrace` 从看门狗线程读取：ART 上这是一次 checkpoint 采样，
 * 不需要挂起主线程，也不会因为主线程在 native 里而卡住看门狗。
 *
 * 日志位置与群打卡自证日志同目录：
 * - 主：`<宿主私有 files>/<模块目录>/main_thread_stall.log`
 * - 镜像：`/sdcard/Android/data/<宿主包名>/files/<模块名>/main_thread_stall.log`
 * 单文件上限 [MAX_BYTES]，超出后滚动为 `.1`（只保留上一份），不会无限增长。
 */
internal object StallWatchdog {

    private const val FILE_NAME = "main_thread_stall.log"
    private const val FILE_NAME_BACKUP = "$FILE_NAME.1"

    /** 心跳间隔；也是判定精度。 */
    private const val HEARTBEAT_MS = 1_000L

    /** 心跳延迟超过这个值才算"卡住"，避免把正常的 jank 也写进日志。 */
    private const val STALL_MS = 4_000L

    /** 整个进程被冻结（后台/doze）时两个线程一起停，恢复后会误判，用这个阈值丢弃。 */
    private const val FREEZE_GUARD_MS = HEARTBEAT_MS * 3

    private const val MAX_BYTES = 512 * 1024
    private const val MAX_STACK_LINES = 80
    private const val MAX_STARTUP_LINES = 24

    private val installed = AtomicBoolean(false)
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Volatile
    private var lastBeatAt = SystemClock.elapsedRealtime()

    @Volatile
    var lastDumpPath: String = ""
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var dumpCount: Int = 0
        private set

    /** 只挂在 UI 主进程与消息服务进程：其它进程没有会让界面停住的主线程工作。 */
    fun install() {
        if (!ProcUtil.isMain && !ProcUtil.isMSF) return
        if (!installed.compareAndSet(false, true)) return

        val mainThread = runCatching { Looper.getMainLooper()?.thread }.getOrNull() ?: return
        val handler = Handler(Looper.getMainLooper())

        val heartbeat = object : Runnable {
            override fun run() {
                lastBeatAt = SystemClock.elapsedRealtime()
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
        handler.post(heartbeat)

        Thread({
            var reportedBeat = Long.MIN_VALUE
            var lastLoopAt = SystemClock.elapsedRealtime()
            while (true) {
                try {
                    Thread.sleep(HEARTBEAT_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val now = SystemClock.elapsedRealtime()
                // 看门狗线程自己也被冻结过：说明整个进程停了，不是主线程卡住。
                if (now - lastLoopAt > FREEZE_GUARD_MS) {
                    lastLoopAt = now
                    reportedBeat = Long.MIN_VALUE
                    continue
                }
                lastLoopAt = now

                val beat = lastBeatAt
                val blockedMs = now - beat
                if (blockedMs < STALL_MS || beat == reportedBeat) continue
                reportedBeat = beat
                runCatching { dump(mainThread, blockedMs) }
                    .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            }
        }, "TCQT-StallWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** 给界面展示：阈值 + 实际写入位置。 */
    fun statusText(): String {
        val primary = runCatching { primaryFile().absolutePath }.getOrDefault("")
        val mirror = runCatching { mirrorFile()?.absolutePath }.getOrNull().orEmpty()
        return buildString {
            append("主线程阻塞超过 ${STALL_MS}ms 时自动记录堆栈与阶段标记\n")
            append("主日志：").append(primary)
            if (mirror.isNotBlank()) append("\n镜像：").append(mirror)
            if (dumpCount > 0) append("\n本进程已记录 ").append(dumpCount).append(" 次")
            lastError.takeIf { it.isNotBlank() }?.let { append("\n最近写入失败：").append(it) }
        }
    }

    private fun dump(mainThread: Thread, blockedMs: Long) {
        val stack = runCatching { mainThread.stackTrace }.getOrDefault(emptyArray())
        val text = buildString {
            append("=== ").append(LocalDateTime.now().format(timeFormatter))
            append("  主线程阻塞 ").append(blockedMs).append("ms ===\n")
            append("进程: ").append(ProcUtil.procSuffix)
            append("  pid=").append(Process.myPid())
            append("  线程: ").append(mainThread.name)
            append("  state=").append(mainThread.state).append('\n')

            append("[主线程堆栈]\n")
            if (stack.isEmpty()) {
                append("    (栈为空：可能停在 native 调用内)\n")
            } else {
                stack.take(MAX_STACK_LINES).forEach { append("    at ").append(it).append('\n') }
                if (stack.size > MAX_STACK_LINES) {
                    append("    ... 其余 ").append(stack.size - MAX_STACK_LINES).append(" 帧省略\n")
                }
            }

            val phases = RuntimePhase.snapshot()
            append("[阶段标记] 最近 ").append(phases.size).append(" 条（→ 表示仍在执行）\n")
            if (phases.isEmpty()) {
                append("    (无)\n")
            } else {
                phases.forEach { append("    ").append(it).append('\n') }
            }

            val startup = StartupTrace.summaryText().lines().take(MAX_STARTUP_LINES)
            if (startup.isNotEmpty()) {
                append("[启动分段]\n")
                startup.forEach { append("    ").append(it).append('\n') }
            }
        }

        write(mirrorFile(), text)
        val primary = primaryFile()
        write(primary, text)
        lastDumpPath = primary.absolutePath
        dumpCount += 1
        Log.w("主线程阻塞 ${blockedMs}ms，已记录：${primary.absolutePath}")
    }

    private fun write(file: File?, text: String) {
        file ?: return
        runCatching {
            file.parentFile?.mkdirs()
            if (file.length() + text.length > MAX_BYTES) {
                val backup = File(file.parentFile, FILE_NAME_BACKUP)
                backup.delete()
                if (!file.renameTo(backup)) file.delete()
            }
            file.appendText(text, Charsets.UTF_8)
        }.onFailure {
            lastError = it.message ?: it.javaClass.simpleName
        }
    }

    private fun primaryFile(): File = File(HookEnv.moduleDataPath, FILE_NAME)

    private fun mirrorFile(): File? = runCatching {
        HookEnv.hostAppContext.getExternalFilesDir(null)
            ?.let { File(it, "${TCQTBuild.APP_NAME}/$FILE_NAME") }
    }.getOrNull()
}
