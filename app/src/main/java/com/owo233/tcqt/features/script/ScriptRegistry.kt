package com.owo233.tcqt.features.script

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 运行中脚本的注册表。
 *
 * 宿主事件的派发方（[ScriptCallbacks] / 管线）只从这里取快照，
 * 不直接接触 [ScriptCore] 的装载逻辑。
 */
internal object ScriptRegistry {

    private val runtimes = CopyOnWriteArrayList<ScriptRuntime>()

    val hasRunning: Boolean get() = runtimes.isNotEmpty()

    fun runtimes(): List<ScriptRuntime> = runtimes.toList()

    fun attach(runtime: ScriptRuntime) {
        if (runtime !in runtimes) runtimes.add(runtime)
    }

    fun detach(runtime: ScriptRuntime) {
        runtimes.remove(runtime)
    }

    fun clear() {
        runtimes.clear()
    }
}
