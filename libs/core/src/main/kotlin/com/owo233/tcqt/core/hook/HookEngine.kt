package com.owo233.tcqt.core.hook

import java.lang.reflect.Member

fun interface Unhook {

    fun unhook()
}

interface Invoker {

    fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any?
    fun invokeWithMaxPriority(maxPriority: Int, thisObject: Any?, vararg args: Any?): Any?
}

interface HookParam {

    val method: Member

    /** Null for static methods; a constructor receives its allocated instance. */
    val thisObject: Any?

    var args: Array<Any?>
    var result: Any?
    var throwable: Throwable?
}

interface Chain : HookParam {

    fun proceed(args: Array<Any?> = this.args): Any?
}

interface IHookEngine {

    // Before callbacks run in descending priority; after callbacks unwind in
    // reverse order. Setting either outcome clears the other and returns early.
    // Before/after callback failures restore the outcome; replacement failures
    // propagate. Original invocation unwraps reflection's transport exception.

    val apiLevel: Int
    val frameworkName: String
    val frameworkVersion: String
    val frameworkVersionCode: Long
    val bridgeClass: Class<*>?

    fun hookBefore(method: Member, priority: Int = 50, callback: (HookParam) -> Unit): Unhook
    fun hookAfter(method: Member, priority: Int = 50, callback: (HookParam) -> Unit): Unhook
    fun hookReplace(method: Member, priority: Int = 50, callback: (Chain) -> Any?): Unhook

    fun getInvoker(method: Member): Invoker
    fun deoptimize(method: Member): Boolean

    fun log(priority: Int, tag: String?, msg: String, t: Throwable? = null)
}

/**
 * `IHookEngine.frameworkName` 的已知取值。
 *
 * 功能侧判断引擎种类请比较这些常量，不要 `is` 判断具体引擎类（会让 `features` 依赖 `loader`）。
 */
object HookFramework {

    /** `ZygiskHookEngine.frameworkName` 的取值。 */
    const val ZYGISK: String = "Zygisk"
}

