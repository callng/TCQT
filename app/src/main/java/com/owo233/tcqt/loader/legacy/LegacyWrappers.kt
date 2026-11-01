package com.owo233.tcqt.loader.legacy

import com.owo233.tcqt.core.hook.Chain
import com.owo233.tcqt.core.hook.HookParam
import com.owo233.tcqt.core.hook.Invoker
import com.owo233.tcqt.core.hook.HookContract
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

class LegacyHookParam(val param: XC_MethodHook.MethodHookParam) : HookParam {

    override val method: Member get() = param.method
    override val thisObject: Any? get() = param.thisObject

    override var args: Array<Any?>
        get() = param.args
        set(value) {
            param.args = value
        }

    override var result: Any?
        get() = param.result
        set(value) {
            param.result = HookContract.result(method, value)
        }

    override var throwable: Throwable?
        get() = param.throwable
        set(value) {
            if (value == null) param.result = HookContract.result(method, null)
            else param.throwable = value
        }
}

class LegacyChain(private val legacyParam: LegacyHookParam) : Chain, HookParam by legacyParam {

    constructor(param: XC_MethodHook.MethodHookParam) : this(LegacyHookParam(param))

    override fun proceed(args: Array<Any?>): Any? {
        return HookContract.original { XposedBridge.invokeOriginalMethod(method, legacyParam.param.thisObject, args) }
    }
}

class LegacyInvoker(private val method: Member) : Invoker {

    override fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any? {
        return HookContract.original { XposedBridge.invokeOriginalMethod(method, thisObject, arrayOf(*args)) }
    }

    override fun invokeWithMaxPriority(
        maxPriority: Int,
        thisObject: Any?,
        vararg args: Any?
    ): Any? {
        // Legacy API 不支持 maxPriority，直接降级
        return invokeOrigin(thisObject, *args)
    }
}
