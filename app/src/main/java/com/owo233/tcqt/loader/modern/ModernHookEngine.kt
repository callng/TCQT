package com.owo233.tcqt.loader.modern

import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.IHookEngine
import com.owo233.tcqt.loader.api.Invoker
import com.owo233.tcqt.loader.api.Unhook
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member

class ModernHookEngine(
    private val base: XposedInterface,
    private val oldHookHandles: List<XposedInterface.HookHandle> = emptyList()
) : IHookEngine {

    private val oldHookHandlesMap = oldHookHandles
        .filter { it.id != null }
        .associateBy { it.id!! }
        .toMutableMap()

    override val apiLevel: Int = base.apiVersion
    override val frameworkName: String = base.frameworkName
    override val frameworkVersion: String = base.frameworkVersion
    override val frameworkVersionCode: Long = base.frameworkVersionCode
    override val bridgeClass: Class<*>? = null

    private fun getHookId(method: Member, priority: Int, type: String): String {
        val methodSig = when (method) {
            is java.lang.reflect.Method -> {
                "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString(",") { it.name }}):${method.returnType.name}"
            }
            is java.lang.reflect.Constructor<*> -> {
                "${method.declaringClass.name}#<init>(${method.parameterTypes.joinToString(",") { it.name }})"
            }
            else -> method.toString()
        }
        return "$methodSig@$priority@$type"
    }

    private fun registerOrReplaceHook(
        method: Member,
        priority: Int,
        type: String,
        hooker: XposedInterface.Hooker
    ): Unhook {
        val hookId = getHookId(method, priority, type)
        val oldHandle = oldHookHandlesMap.remove(hookId)
        val handle = if (oldHandle != null) {
            try {
                oldHandle.replaceHook(hooker)
                oldHandle
            } catch (t: Throwable) {
                log(3, "ModernHookEngine", "Failed to replace hook $hookId: ${t.message}", t)
                base.hook(method as Executable).setId(hookId).setPriority(priority).intercept(hooker)
            }
        } else {
            base.hook(method as Executable).setId(hookId).setPriority(priority).intercept(hooker)
        }
        return Unhook { handle.unhook() }
    }

    override fun hookBefore(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        return registerOrReplaceHook(method, priority, "before") { chain ->
            val param = ModernHookParam(chain)
            callback(param)
            if (param.isReturnEarly) {
                param.result
            } else {
                chain.proceed(param.args)
            }
        }
    }

    override fun hookAfter(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        return registerOrReplaceHook(method, priority, "after") { chain ->
            val param = ModernHookParam(chain)
            try {
                param.result = chain.proceed(param.args)
            } catch (t: Throwable) {
                param.throwable = t
            }
            callback(param)
            if (param.throwable != null) throw param.throwable!!
            param.result
        }
    }

    override fun hookReplace(method: Member, priority: Int, callback: (Chain) -> Any?): Unhook {
        return registerOrReplaceHook(method, priority, "replace") { chain ->
            val modernChain = ModernChain(chain)
            callback(modernChain)
        }
    }

    fun cleanUpOldHooks() {
        oldHookHandlesMap.values.forEach { handle ->
            runCatching {
                handle.unhook()
            }.onFailure { t ->
                log(3, "ModernHookEngine", "Failed to unhook obsolete hook ${handle.id}: ${t.message}", t)
            }
        }
        oldHookHandlesMap.clear()
    }

    override fun getInvoker(method: Member): Invoker {
        return ModernInvoker(base, method)
    }

    override fun deoptimize(method: Member): Boolean {
        return base.deoptimize(method as Executable)
    }

    override fun log(priority: Int, tag: String?, msg: String, t: Throwable?) {
        if (t != null) {
            base.log(priority, tag, msg, t)
        } else {
            base.log(priority, tag, msg)
        }
    }
}
