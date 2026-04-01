package com.owo233.tcqt.loader.modern

import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.Invoker
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

class ModernHookParam(val chain: XposedInterface.Chain) : HookParam {

    override val method: Member get() = chain.executable

    override val thisObject: Any get() = chain.thisObject

    override var args: Array<Any?> = chain.args.toTypedArray()

    var isReturnEarly = false
        private set

    override var result: Any? = null
        set(value) {
            field = value
            isReturnEarly = true
        }

    override var throwable: Throwable? = null
}

class ModernChain(private val modernParam: ModernHookParam) : Chain, HookParam by modernParam {
    constructor(chain: XposedInterface.Chain) : this(ModernHookParam(chain))

    override fun proceed(args: Array<Any?>): Any? {
        return modernParam.chain.proceed(args)
    }
}

class ModernInvoker(
    private val base: XposedInterface,
    private val method: Member
) : Invoker {

    override fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any? {
        val invokeType = XposedInterface.Invoker.Type.ORIGIN

        return when (val m = method) {
            is Method -> base.getInvoker(m).setType(invokeType).invoke(thisObject, *args)
            is Constructor<*> -> base.getInvoker(m).setType(invokeType).newInstance(*args)
            else -> throw IllegalArgumentException("Unsupported member type: ${m.javaClass}")
        }
    }

    override fun invokeWithMaxPriority(
        maxPriority: Int,
        thisObject: Any?,
        vararg args: Any?
    ): Any? {
        val invokeType = XposedInterface.Invoker.Type.Chain(maxPriority)

        return when (val m = method) {
            is Method -> base.getInvoker(m).setType(invokeType).invoke(thisObject, *args)
            is Constructor<*> -> base.getInvoker(m).setType(invokeType).newInstance(*args)
            else -> throw IllegalArgumentException("Unsupported member type: ${m.javaClass}")
        }
    }
}
