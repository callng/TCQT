package com.owo233.tcqt.core.log

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.TCQTBuild
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 崩溃日志收集。
 *
 * 设计要点：
 * 1. 崩溃时必须**同步**写盘——FileLog 走的是单线程 executor，进程被系统 kill 时
 *    队列里的日志会直接丢失，所以这里独立实现，不走 FileLog。
 * 2. 崩溃现场（堆栈）之外，还会附带日志文件尾部，便于看出崩溃前最后做了什么。
 * 3. 每次崩溃会同时覆盖一份 pending.txt；下次模块启动时 [install] 会把它归档成
 *    crash-<时间>.txt —— 即「QQ 崩溃后，再次打开自动收集」。
 *
 * 注意：这里只能捕获 Java 层未处理异常。native SIGSEGV 走不到这里，
 * 那种情况依赖 pending.txt 在下次启动时被归档来提示「上次是异常退出」。
 */
internal object CrashCatcher {

    private const val SUB_DIR = "crash"
    private const val PENDING = "pending.txt"
    private const val LATEST = "latest.txt"
    private const val TAIL_LINES = 300

    private val installed = AtomicBoolean(false)
    private val fileNameFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val fullFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /** 崩溃日志目录：<externalMediaDirs>/<APP_NAME>/crash */
    fun crashDir(): File? = runCatching {
        val ctx = HookEnv.hostAppContext
        @Suppress("DEPRECATION")
        ctx.externalMediaDirs
            ?.firstOrNull { it != null && it.canWrite() }
            ?.let { File(it, "${TCQTBuild.APP_NAME}/$SUB_DIR") }
            ?.apply { if (!exists()) mkdirs() }
    }.getOrNull()

    private const val BREADCRUMB = "last_step.txt"

    /**
     * 面包屑：关键流程每走一步就**同步**追加一行。
     *
     * 用来对付「什么都没留下」的崩溃（LMK 杀进程 / native SIGSEGV）：
     * 进程被瞬间杀死时，只有已经落到磁盘的内容还在。
     * 下次进来读 [lastStep] 就知道上一次死在哪个环节。
     */
    fun breadcrumb(step: String) {
        val dir = crashDir() ?: return
        runCatching {
            val line = "${LocalDateTime.now().format(fullFmt)} | $step\n"
            File(dir, BREADCRUMB).appendText(line, StandardCharsets.UTF_8)
        }
    }

    /** 读取最后一条面包屑（用于排查上次崩溃点）。 */
    fun lastStep(): String = runCatching {
        val dir = crashDir() ?: return@runCatching "(无 crash 目录)"
        val f = File(dir, BREADCRUMB)
        if (!f.exists()) return@runCatching "(无面包屑)"
        f.readLines().takeLast(15).joinToString("\n")
    }.getOrElse { "(读取失败: ${it.message})" }

    /** 安装全局异常捕获。应在每个宿主进程的初始化阶段调用一次。 */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { dumpToDisk(thread, throwable) }
            // 仍然交还给原 handler，保证 QQ 自己的崩溃上报/系统行为不受影响
            if (previous != null) {
                runCatching { previous.uncaughtException(thread, throwable) }
            }
        }
        runCatching { archivePending() }
    }

    /**
     * 由宿主初始化完成后调用：把上次崩溃的 pending 归档。
     * 注入早期 HookEnv.hostAppContext 还没就绪，所以延迟重试。
     */
    fun scheduleArchivePending() {
        Thread {
            runCatching { Thread.sleep(10_000) }
            runCatching { archivePending() }
        }.apply {
            isDaemon = true
            name = "TCQT-CrashArchive"
        }.start()
    }

    /** 启动时：把上次崩溃遗留的 pending 归档（这就是「再次打开自动收集」）。 */
    private fun archivePending() {
        val dir = crashDir() ?: return
        val pending = File(dir, PENDING)
        if (!pending.exists() || pending.length() == 0L) return
        val dt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(pending.lastModified()),
            java.time.ZoneId.systemDefault()
        )
        val target = File(dir, "crash-${dt.format(fileNameFmt)}.txt")
        // 覆盖式归档，保留 pending 供 latest 查看
        runCatching {
            if (target.exists()) target.delete()
            pending.copyTo(target, overwrite = true)
        }
    }

    private fun dumpToDisk(thread: Thread, throwable: Throwable) {
        val dir = crashDir() ?: return
        val now = LocalDateTime.now()
        val text = buildString {
            appendLine("================ TCQT 崩溃报告 ================")
            appendLine("时间   : ${now.format(fullFmt)}")
            appendLine("进程   : ${runCatching { ProcUtil.procSuffix }.getOrDefault("?")}")
            appendLine("线程   : ${thread.name}")
            appendLine("异常   : ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("=============== 堆栈 ===============")
            appendLine(throwable.stackTraceToString())
            appendLine("========== 崩溃前日志尾部(${TAIL_LINES} 行) ==========")
            append(tailOfLog(TAIL_LINES))
            appendLine()
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        // 同步写：不加锁、不用线程池，尽量在进程被杀前落盘
        runCatching {
            File(dir, "crash-${now.format(fileNameFmt)}.txt").writeBytes(bytes)
            File(dir, LATEST).writeBytes(bytes)
            File(dir, PENDING).writeBytes(bytes)
        }
    }

    /** 读取 FileLog 正在写的那个 log.txt 的末 n 行。 */
    private fun tailOfLog(lines: Int): String = runCatching {
        val ctx = HookEnv.hostAppContext
        @Suppress("DEPRECATION")
        val logFile = ctx.externalMediaDirs
            ?.firstOrNull { it != null && it.canWrite() }
            ?.let { File(File(it, TCQTBuild.APP_NAME), "log/log.txt") }
            ?: return@runCatching "(无法定位日志文件)"
        if (!logFile.exists()) return@runCatching "(日志文件不存在)"

        val ring = ArrayDeque<String>()
        logFile.bufferedReader(StandardCharsets.UTF_8).useLines { seq ->
            seq.forEach { line ->
                if (ring.size >= lines) ring.removeFirst()
                ring.addLast(line)
            }
        }
        ring.joinToString("\n")
    }.getOrElse { "(读取日志失败: ${it.message})" }
}
