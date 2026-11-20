package com.owo233.tcqt.core.action

import android.os.SystemClock
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 启动分段计时（release 也生效）。
 *
 * 现场反馈“装了模块后打开 QQ 明显变慢”，但日志里只有各阶段汇总，无法判断慢在
 * DexKit 枚举、hook 安装还是好友历史初始化。这里按时间顺序记录模块入口之后的每个
 * 节点，设置页“启动耗时”直接展示，不依赖取日志。
 *
 * 只在启动路径写少量标记，不做主动采样，正常情况下对启动耗时的扰动可以忽略。
 */
internal object StartupTrace {

    private const val TAG = "StartupTrace"
    private const val MAX_MARKS = 64
    private const val MAX_FILE_BYTES = 96 * 1024L

    private data class Mark(
        val label: String,
        val atMs: Long,
        val threadName: String,
    )

    private val marks = ConcurrentLinkedQueue<Mark>()
    private val origin = AtomicLong(SystemClock.elapsedRealtime())
    private val persisted = AtomicBoolean(false)

    /** 进程内模块加载入口调用：重置计时起点。 */
    fun begin(label: String = "module-entry") {
        marks.clear()
        origin.set(SystemClock.elapsedRealtime())
        persisted.set(false)
        mark(label)
    }

    fun mark(label: String) {
        if (marks.size >= MAX_MARKS) return
        marks += Mark(label, SystemClock.elapsedRealtime(), Thread.currentThread().name)
    }

    inline fun <T> measure(label: String, block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            mark("$label=${SystemClock.elapsedRealtime() - startedAt}ms")
        }
    }

    fun summaryText(): String {
        val list = marks.toList()
        if (list.isEmpty()) return "本进程尚未记录启动耗时"
        val base = origin.get()
        return buildString {
            append("相对模块入口（+ 总耗时，Δ 距上一节点）\n\n")
            var previous = base
            list.forEach { item ->
                append('+').append(item.atMs - base).append("ms")
                append("  Δ").append(item.atMs - previous).append("ms")
                append("  ").append(item.label)
                if (item.threadName.isNotBlank() && item.threadName != "main") {
                    append("  [").append(item.threadName).append(']')
                }
                append('\n')
                previous = item.atMs
            }
            append("\n宿主：").append(HookEnv.versionName).append("(").append(HookEnv.versionCode).append(')')
            append("  模块：").append(TCQTBuild.VER_NAME).append("\n")
            append("（节点带 =xxms 表示该步骤自身耗时）")
        }
    }

    /** 每个启动周期写一次日志文件，便于现场直接把文件发回来。 */
    fun persist() {
        if (!persisted.compareAndSet(false, true)) return
        runCatching {
            val directory = File(HookEnv.moduleDataPath, "global").apply { mkdirs() }
            val file = File(directory, "startup_perf.log")
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                file.writeText("")
            }
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("=== $time ${TCQTBuild.VER_NAME} ===\n${summaryText()}\n\n")
        }.onFailure {
            Log.w("$TAG: persist failed", it)
        }
    }
}
