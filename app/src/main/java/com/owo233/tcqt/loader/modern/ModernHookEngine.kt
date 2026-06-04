package com.owo233.tcqt.loader.modern

import android.annotation.SuppressLint
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

    @SuppressLint("XposedNewApi")
    private val oldHookHandlesMap = if (base.apiVersion >= 102) {
        oldHookHandles
            .filter { it.id != null }
            .associateBy { it.id!! }
            .toMutableMap()
    } else {
        mutableMapOf()
    }

    override val apiLevel: Int = base.apiVersion
    override val frameworkName: String = base.frameworkName
    override val frameworkVersion: String = base.frameworkVersion
    override val frameworkVersionCode: Long = base.frameworkVersionCode
    override val bridgeClass: Class<*>? = null

    private val hookCounter = mutableMapOf<String, Int>()

    private fun getHookId(method: Member, priority: Int, type: String): String {
        val tag = com.owo233.tcqt.loader.api.HookEngineManager.currentTag.get()
        val tagPrefix = if (!tag.isNullOrEmpty()) "$tag->" else ""
        val methodSig = when (method) {
            is java.lang.reflect.Method -> {
                "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString(",") { it.name }}):${method.returnType.name}"
            }
            is java.lang.reflect.Constructor<*> -> {
                "${method.declaringClass.name}#<init>(${method.parameterTypes.joinToString(",") { it.name }})"
            }
            else -> method.toString()
        }
        val baseId = "$tagPrefix$methodSig@$priority@$type"
        val count = synchronized(hookCounter) {
            val current = hookCounter[baseId] ?: 0
            hookCounter[baseId] = current + 1
            current
        }
        return if (count == 0) baseId else "$baseId#$count"
    }

    @SuppressLint("XposedNewApi")
    private fun registerOrReplaceHook(
        method: Member,
        priority: Int,
        type: String,
        hooker: XposedInterface.Hooker
    ): Unhook {
        val hookId = getHookId(method, priority, type)
        val oldHandle = oldHookHandlesMap.remove(hookId)
        val handle = if (oldHandle != null && base.apiVersion >= 102) {
            try {
                oldHandle.replaceHook(hooker)
                oldHandle
            } catch (t: Throwable) {
                log(3, "ModernHookEngine", "Failed to replace hook $hookId: ${t.message}", t)
                var builder = base.hook(method as Executable)
                if (base.apiVersion >= 102) {
                    builder = builder.setId(hookId)
                }
                builder.setPriority(priority).intercept(hooker)
            }
        } else {
            var builder = base.hook(method as Executable)
            if (base.apiVersion >= 102) {
                builder = builder.setId(hookId)
            }
            builder.setPriority(priority).intercept(hooker)
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

    @SuppressLint("XposedNewApi")
    fun cleanUpOldHooks() {
        oldHookHandlesMap.values.forEach { handle ->
            runCatching {
                handle.unhook()
            }.onFailure { t ->
                val idStr = if (base.apiVersion >= 102) " ${handle.id}" else ""
                log(3, "ModernHookEngine", "Failed to unhook obsolete hook$idStr: ${t.message}", t)
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
