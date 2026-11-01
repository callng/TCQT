package com.owo233.tcqt.loader.modern

import com.owo233.tcqt.core.hook.Chain
import com.owo233.tcqt.core.hook.HookParam
import com.owo233.tcqt.core.hook.Invoker
import com.owo233.tcqt.core.hook.HookCallState
import com.owo233.tcqt.core.hook.HookContract
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

internal class ModernHookParam(val chain: XposedInterface.Chain) : HookCallState(
    chain.executable, chain.thisObject, chain.args.toTypedArray(),
)

internal class ModernChain(private val modernParam: ModernHookParam) : Chain, HookParam by modernParam {

    constructor(chain: XposedInterface.Chain) : this(ModernHookParam(chain))

    override fun proceed(args: Array<Any?>): Any? {
        return HookContract.original { modernParam.chain.proceed(args) }
    }
}

class ModernInvoker(
    private val base: XposedInterface,
    private val method: Member
) : Invoker {

    override fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any? {
        val invokeType = XposedInterface.Invoker.Type.ORIGIN

        return HookContract.original { when (val m = method) {
            is Method -> base.getInvoker(m).setType(invokeType).invoke(thisObject, *args)
            is Constructor<*> -> base.getInvoker(m).setType(invokeType).newInstance(*args)
            else -> throw IllegalArgumentException("Unsupported member type: ${m.javaClass}")
        } }
    }

    override fun invokeWithMaxPriority(
        maxPriority: Int,
        thisObject: Any?,
        vararg args: Any?
    ): Any? {
        val invokeType = XposedInterface.Invoker.Type.Chain(maxPriority)

        return HookContract.original { when (val m = method) {
            is Method -> base.getInvoker(m).setType(invokeType).invoke(thisObject, *args)
            is Constructor<*> -> base.getInvoker(m).setType(invokeType).newInstance(*args)
            else -> throw IllegalArgumentException("Unsupported member type: ${m.javaClass}")
        } }
    }
}
