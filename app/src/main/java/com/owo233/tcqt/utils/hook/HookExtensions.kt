package com.owo233.tcqt.utils.hook

import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.Unhook
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.MethodSearcher
import com.owo233.tcqt.utils.reflect.callOriginal
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.isCompatibleWith
import java.lang.reflect.Member
import java.lang.reflect.Method

fun Member.hookBefore(
    block: (HookParam) -> Unit
): Unhook {
    return HookEngineManager.engine.hookBefore(this) { param ->
        runCatching { block(param) }.onFailure { Log.e("HookBeforeError", it) }
    }
}

fun Member.hookAfter(
    block: (HookParam) -> Unit
): Unhook {
    return HookEngineManager.engine.hookAfter(this) { param ->
        runCatching { block(param) }.onFailure { Log.e("HookAfterError", it) }
    }
}

fun Member.hookReplace(
    block: (Chain) -> Any?
): Unhook {
    return HookEngineManager.engine.hookReplace(this) { chain ->
        try {
            block(chain)
        } catch (t: Throwable) {
            Log.e("HookReplaceError", t)
            chain.proceed()
        }
    }
}

fun Class<*>.hookMethodBefore(
    searcherBlock: MethodSearcher.() -> Unit,
    block: (HookParam) -> Unit
): Unhook {
    val method = findMethod(searcherBlock)
    return method.hookBefore(block)
}

fun Class<*>.hookMethodAfter(
    searcherBlock: MethodSearcher.() -> Unit,
    block: (HookParam) -> Unit
): Unhook {
    val method = findMethod(searcherBlock)
    return method.hookAfter(block)
}

fun Class<*>.hookMethodReplace(
    searcherBlock: MethodSearcher.() -> Unit,
    block: (Chain) -> Any?
): Unhook {
    val method = findMethod(searcherBlock)
    return method.hookReplace(block)
}

fun Class<*>.hookMethodBefore(
    name: String,
    vararg paramTypes: Class<*>?,
    block: (HookParam) -> Unit
): Unhook {
    val method = findMethod { this.name = name; paramTypes(*paramTypes) }
    return method.hookBefore(block)
}

fun Class<*>.hookMethodAfter(
    name: String,
    vararg paramTypes: Class<*>?,
    block: (HookParam) -> Unit
): Unhook {
    val method = findMethod { this.name = name; paramTypes(*paramTypes) }
    return method.hookAfter(block)
}

fun Class<*>.hookMethodReplace(
    name: String,
    vararg paramTypes: Class<*>?,
    block: (Chain) -> Any?
): Unhook {
    val method = findMethod { this.name = name; paramTypes(*paramTypes) }
    return method.hookReplace(block)
}

fun Chain.invokeOriginal(args: Array<Any?> = arrayOf()): Any? {
    return method.callOriginal(thisObject, *args.ifEmpty { this.args })
}

fun Member.returnConstant(constant: Any?): Unhook {
    return this.hookReplace { constant }
}

fun Member.doNothing(): Unhook {
    return this.hookReplace { null }
}

inline fun <reified T> HookParam.getFirstArg(): T? {
    return args.find { it is T } as? T
}

inline fun <reified T> HookParam.getArgByType(): T? {
    val methodObj = this.method as? Method ?: return null
    val index = methodObj.parameterTypes.indexOfFirst { T::class.java.isAssignableFrom(it) }
    if (index != -1 && index < args.size) {
        return args[index] as? T
    }
    return null
}

inline fun <reified T> Member.replaceFirstParam(
    newValue: T
): Unhook {
    return this.hookBefore { param ->
        val method = param.method as? Method ?: return@hookBefore
        val index = method.parameterTypes.indexOfFirst { it.isCompatibleWith(T::class.java) }
        if (index != -1 && index < param.args.size) {
            param.args[index] = newValue
        }
    }
}

fun Member.replaceParam(
    index: Int,
    value: Any?
): Unhook {
    return this.hookBefore { param ->
        if (index in param.args.indices) {
            param.args[index] = value
        }
    }
}
