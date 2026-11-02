package com.owo233.tcqt.loader.modern

import com.owo233.tcqt.core.hook.Chain
import com.owo233.tcqt.core.hook.HookContract
import com.owo233.tcqt.core.hook.HookParam
import com.owo233.tcqt.core.hook.IHookEngine
import com.owo233.tcqt.core.hook.Invoker
import com.owo233.tcqt.core.hook.Unhook
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member

class ModernHookEngine(private val base: XposedInterface) : IHookEngine {

    override val apiLevel: Int = base.apiVersion
    override val frameworkName: String = base.frameworkName
    override val frameworkVersion: String = base.frameworkVersion
    override val frameworkVersionCode: Long = base.frameworkVersionCode
    override val bridgeClass: Class<*>? = null

    override fun hookBefore(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        val handle = base.hook(method as Executable).setPriority(priority).intercept { chain ->
            val param = ModernHookParam(chain)
            val outcome = param.snapshot()
            try { callback(param) } catch (error: Throwable) {
                log(6, "TCQT", "Before callback failed: $method", error)
                param.restore(outcome)
            }
            if (param.isReturnEarly) return@intercept param.finish()
            return@intercept chain.proceed(param.args)
        }
        return Unhook { handle.unhook() }
    }

    override fun hookAfter(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        val handle = base.hook(method as Executable).setPriority(priority).intercept { chain ->
            val param = ModernHookParam(chain)
            try {
                param.result = chain.proceed(param.args)
            } catch (t: Throwable) {
                param.throwable = t
            }
            val outcome = param.snapshot()
            try { callback(param) } catch (error: Throwable) {
                log(6, "TCQT", "After callback failed: $method", error)
                param.restore(outcome)
            }
            return@intercept param.finish()
        }
        return Unhook { handle.unhook() }
    }

    override fun hookReplace(method: Member, priority: Int, callback: (Chain) -> Any?): Unhook {
        val handle = base.hook(method as Executable).setPriority(priority).intercept { chain ->
            val modernChain = ModernChain(chain)
            return@intercept HookContract.result(method, callback(modernChain))
        }
        return Unhook { handle.unhook() }
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
