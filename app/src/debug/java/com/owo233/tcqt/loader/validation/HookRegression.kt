package com.owo233.tcqt.loader.validation

import androidx.annotation.Keep
import com.owo233.tcqt.core.hook.HookContract
import com.owo233.tcqt.core.hook.IHookEngine
import com.owo233.tcqt.core.hook.Unhook
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Keep
internal class HookRegressionTarget {
    var calls = 0
    fun number(value: Int): Int { calls++; return value + 1 }
    fun failing(): Int { calls++; throw IllegalArgumentException("original-fixture") }
    fun concurrent(value: Int): Int = value + 1
    companion object {
        @JvmStatic fun staticNumber(value: Int): Int = value + 1
    }
}

internal object HookRegression {
    fun lifecycle(engine: IHookEngine): Int {
        val target = HookRegressionTarget()
        val method = target.javaClass.getDeclaredMethod("concurrent", Int::class.javaPrimitiveType)
        fun invoke() = HookContract.original { method.invoke(target, 1) }
        val count = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val handles = ArrayList<Unhook>()
        try {
            handles += (0 until 40).map { pool.submit<Unhook> {
                engine.hookAfter(method, it) { count.incrementAndGet() }
            } }.map { it.get(5, TimeUnit.SECONDS) }
            check(invoke() == 2 && count.get() == 40)
            handles.map { handle -> pool.submit { handle.unhook(); handle.unhook() } }
                .forEach { it.get(5, TimeUnit.SECONDS) }
            handles.clear()
            check(invoke() == 2 && count.get() == 40)

            lateinit var selfRemoving: Unhook
            selfRemoving = engine.hookBefore(method) { count.incrementAndGet(); selfRemoving.unhook() }
            handles += selfRemoving
            check(invoke() == 2 && invoke() == 2 && count.get() == 41)
            selfRemoving.unhook(); handles.clear()

            val entered = CountDownLatch(1)
            val resume = CountDownLatch(1)
            val paused = engine.hookBefore(method) { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)) }
            handles += paused
            val call = pool.submit<Any?> { invoke() }
            try {
                check(entered.await(5, TimeUnit.SECONDS))
                paused.unhook()
            } finally { resume.countDown() }
            check(call.get(5, TimeUnit.SECONDS) == 2 && invoke() == 2)
            return 4
        } finally {
            handles.forEach { it.unhook() }
            pool.shutdownNow()
        }
    }

    fun run(engine: IHookEngine): Int {
        val target = HookRegressionTarget()
        val number = target.javaClass.getDeclaredMethod("number", Int::class.javaPrimitiveType)
        val failing = target.javaClass.getDeclaredMethod("failing")
        val static = target.javaClass.getDeclaredMethod("staticNumber", Int::class.javaPrimitiveType)
        val handles = ArrayList<Unhook>()
        var checks = 0
        fun release() { handles.asReversed().forEach { it.unhook() }; handles.clear() }
        fun invoke(value: Int) = HookContract.original { number.invoke(target, value) }
        try {
            val order = ArrayList<String>()
            handles += engine.hookBefore(number, 90) { check(it.thisObject === target); order += "b90"; it.args[0] = 3 }
            handles += engine.hookBefore(number, 10) { order += "b10"; it.args = arrayOf((it.args[0] as Int) + 1) }
            handles += engine.hookAfter(number, 90) { order += "a90"; it.result = (it.result as Int) + 10 }
            handles += engine.hookAfter(number, 10) { order += "a10" }
            check(invoke(1) == 15)
            check(order == listOf("b90", "b10", "a10", "a90"))
            checks++; release()

            handles += engine.hookBefore(static) { check(it.thisObject == null); it.result = 8 }
            check(HookContract.original { static.invoke(null, 1) } == 8)
            checks++; release()

            handles += engine.hookBefore(number) { it.result = 9; error("before-fixture") }
            check(invoke(1) == 2)
            checks++; release()

            handles += engine.hookAfter(number) { it.result = 9; error("after-fixture") }
            check(invoke(1) == 2)
            checks++; release()

            handles += engine.hookBefore(number) { it.throwable = IllegalStateException("early-fixture") }
            val before = target.calls
            check(runCatching { invoke(1) }.exceptionOrNull()?.message == "early-fixture")
            check(target.calls == before)
            checks++; release()

            handles += engine.hookReplace(number) { null }
            check(invoke(1) == 0)
            checks++; release()

            handles += engine.hookReplace(failing) { it.proceed() }
            val calls = target.calls
            check(runCatching { HookContract.original { failing.invoke(target) } }.exceptionOrNull()?.message == "original-fixture")
            check(target.calls == calls + 1)
            checks++; release()

            check(engine.getInvoker(number).invokeOrigin(target, 2) == 3)
            checks++
        } finally { release() }
        return checks
    }
}
