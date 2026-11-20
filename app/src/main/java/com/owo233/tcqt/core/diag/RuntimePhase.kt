package com.owo233.tcqt.core.diag

import android.os.SystemClock
import java.util.ArrayDeque

/**
 * 运行期阶段标记。
 *
 * 和 [com.owo233.tcqt.core.action.StartupTrace] 的区别：StartupTrace 记的是"启动到首帧"
 * 一次性节点的耗时，这里记的是**任意时刻**某线程正在做什么，专门给主线程卡顿看门狗取证用。
 *
 * 只允许在粗粒度入口（一次好友历史回包、一次 DexKit 扫描、一次打卡任务、一个功能安装）
 * 调用，单次开销是"构造一个对象 + 一次加锁入队"，对主线程可忽略；不要放进循环或高频回调。
 *
 * 事件表有硬上限，满了从队头淘汰，所以长时间运行也不会涨内存。
 */
internal object RuntimePhase {

    private const val MAX_EVENTS = 64

    private val originMs = SystemClock.elapsedRealtime()

    private data class Event(
        val atMs: Long,
        val thread: String,
        val label: String,
        val ending: Boolean,
        val costMs: Long,
    )

    private val lock = Any()
    private val events = ArrayDeque<Event>(MAX_EVENTS + 1)

    @PublishedApi
    internal fun begin(label: String): Long {
        record(label, false, -1L)
        return SystemClock.elapsedRealtime()
    }

    @PublishedApi
    internal fun end(label: String, startedAt: Long) {
        record(label, true, SystemClock.elapsedRealtime() - startedAt)
    }

    /** 包一段同步或挂起的逻辑；返回时（含抛异常）自动记一条结束事件。 */
    inline fun <T> section(label: String, block: () -> T): T {
        val startedAt = begin(label)
        try {
            return block()
        } finally {
            end(label, startedAt)
        }
    }

    /** 最近 [MAX_EVENTS] 条事件，按时间正序；只有 `→` 没有对应 `←` 的即为当时仍在执行。 */
    fun snapshot(): List<String> {
        val copy = synchronized(lock) { events.toList() }
        return copy.map { event ->
            val arrow = if (event.ending) "←" else "→"
            val cost = if (event.ending) " ${event.costMs}ms" else ""
            "+${event.atMs}ms [${event.thread}] $arrow ${event.label}$cost"
        }
    }

    private fun record(label: String, ending: Boolean, costMs: Long) {
        val event = Event(
            SystemClock.elapsedRealtime() - originMs,
            Thread.currentThread().name,
            label,
            ending,
            costMs,
        )
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
        }
    }
}
