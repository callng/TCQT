package com.owo233.tcqt.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * 基于 Xposed API 82 的 [HookerBridge] 实现。
 * 通过传统 XposedBridge / XposedHelpers 进行底层 hook。
 */
class XpHookImpl(
    override val modulePath: String
) : HookerBridge {

    override val isLibxposed: Boolean = false

    // --- HookParam 包装 ---

    private class XpHookParam(
        private val delegate: XC_MethodHook.MethodHookParam
    ) : HookerBridge.HookParam {
        override val member: Member get() = delegate.method
        override val thisObject: Any? get() = delegate.thisObject
        override val args: Array<Any?> get() = delegate.args
        override val returnType: Class<*>? get() = (delegate.method as? Method)?.returnType

        override fun setResult(result: Any?) { delegate.result = result }
        override fun getResult(): Any? = delegate.result
        override fun hasResult(): Boolean = delegate.result != null

        companion object {
            private val UNSET = Any()
        }
        override fun setThrowable(throwable: Throwable?) { delegate.throwable = throwable }
        override fun getThrowable(): Throwable? = delegate.throwable
    }

    // --- UnhookHandle 包装 ---

    private class XpUnhookHandle(private val delegate: XC_MethodHook.Unhook) : HookerBridge.UnhookHandle {
        override fun unhook() { delegate.unhook() }
    }

    // --- hook / replace ---

    override fun hookMember(member: Member, priority: Int, callback: HookerBridge.HookCallback): HookerBridge.UnhookHandle {
        val hook = object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                callback.beforeHookedMethod(XpHookParam(param))
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                callback.afterHookedMethod(XpHookParam(param))
            }
        }
        return XpUnhookHandle(XposedBridge.hookMethod(member, hook))
    }

    override fun replaceMember(member: Member, priority: Int, callback: HookerBridge.ReplaceCallback): HookerBridge.UnhookHandle {
        val replacement = object : XC_MethodReplacement(priority) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return callback.replaceHookedMethod(XpHookParam(param))
            }
        }
        return XpUnhookHandle(XposedBridge.hookMethod(member, replacement))
    }

    override fun hookAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: HookerBridge.HookCallback): Set<HookerBridge.UnhookHandle> {
        val hook = object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                callback.beforeHookedMethod(XpHookParam(param))
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                callback.afterHookedMethod(XpHookParam(param))
            }
        }
        return XposedBridge.hookAllMethods(clazz, methodName, hook).map { XpUnhookHandle(it) }.toSet()
    }

    override fun replaceAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: HookerBridge.ReplaceCallback): Set<HookerBridge.UnhookHandle> {
        val replacement = object : XC_MethodReplacement(priority) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return callback.replaceHookedMethod(XpHookParam(param))
            }
        }
        return XposedBridge.hookAllMethods(clazz, methodName, replacement).map { XpUnhookHandle(it) }.toSet()
    }

    override fun hookAllConstructors(clazz: Class<*>, priority: Int, callback: HookerBridge.HookCallback): Set<HookerBridge.UnhookHandle> {
        val hook = object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                callback.beforeHookedMethod(XpHookParam(param))
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                callback.afterHookedMethod(XpHookParam(param))
            }
        }
        return XposedBridge.hookAllConstructors(clazz, hook).map { XpUnhookHandle(it) }.toSet()
    }

    override fun replaceAllConstructors(clazz: Class<*>, priority: Int, callback: HookerBridge.ReplaceCallback): Set<HookerBridge.UnhookHandle> {
        val replacement = object : XC_MethodReplacement(priority) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return callback.replaceHookedMethod(XpHookParam(param))
            }
        }
        return XposedBridge.hookAllConstructors(clazz, replacement).map { XpUnhookHandle(it) }.toSet()
    }

    // --- invokeOriginal ---

    override fun invokeOriginalMethod(method: Method, thisObject: Any?, args: Array<Any?>): Any? {
        return XposedBridge.invokeOriginalMethod(method, thisObject, args)
    }

    override fun invokeOriginalConstructor(constructor: Constructor<*>, thisObject: Any, args: Array<Any?>) {
        // 传统 API 无法直接 invokeOriginalConstructor
        // 使用反射绕过（设置 accessible 并 invoke）
        constructor.isAccessible = true
        constructor.newInstance(*args)
    }

    // --- 日志 ---

    override fun log(message: String) {
        XposedBridge.log(message)
    }

    override fun log(throwable: Throwable) {
        XposedBridge.log(throwable)
    }

    companion object {
        /** 提供给 XposedHelper 使用的兼容方法：findAndHookMethod */
        fun findAndHookMethod(clazz: Class<*>, methodName: String?, vararg parameterTypesAndCallback: Any?): XC_MethodHook.Unhook {
            return XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
        }

        /** 提供给 XposedHelper 使用的兼容方法：findAndHookConstructor */
        fun findAndHookConstructor(clazz: Class<*>, vararg parameterTypesAndCallback: Any?): XC_MethodHook.Unhook {
            return XposedHelpers.findAndHookConstructor(clazz, *parameterTypesAndCallback)
        }

        /** 提供给 XposedHelper 使用的兼容方法：findClass */
        fun findClass(className: String?, classLoader: ClassLoader?): Class<*> {
            return XposedHelpers.findClass(className, classLoader)
        }

        /** 提供给 XposedHelper 使用的兼容方法：findClassIfExists */
        fun findClassIfExists(className: String?, classLoader: ClassLoader?): Class<*>? {
            return XposedHelpers.findClassIfExists(className, classLoader)
        }

        /** 提供给 XposedHelper 使用的兼容方法：XCallback.PRIORITY_DEFAULT */
        val PRIORITY_DEFAULT: Int get() = XCallback.PRIORITY_DEFAULT
    }
}
