package com.owo233.tcqt.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 全局 HookerBridge 管理器。
 *
 * 在传统 Xposed 入口初始化时设为 [XpHookImpl]，
 * 在 libxposed 入口初始化时设为 [LspHookImpl]。
 *
 * [Compat] 提供与 XposedHelpers/XposedBridge 签名兼容的静态方法，
 * 供 XposedHelper 调用而不需要修改上层代码。
 */
object HookerBridgeManager {

    @Volatile
    private var _bridge: HookerBridge? = null

    val bridge: HookerBridge
        get() = _bridge ?: throw IllegalStateException("HookerBridge not initialized yet")

    val isInitialized: Boolean get() = _bridge != null
    val isLibxposed: Boolean get() = _bridge?.isLibxposed == true

    internal fun init(bridge: HookerBridge) {
        check(_bridge == null) { "HookerBridge already initialized" }
        _bridge = bridge
    }

    /**
     * 兼容层：提供与 XposedHelpers/XposedBridge 签名一致的静态方法。
     * 在传统 API 下直接委托给原始 API；在 libxposed 下桥接到 HookerBridge。
     */
    object Compat {

        // ---- Class 查找 ----

        fun findClass(className: String?, classLoader: ClassLoader?): Class<*> {
            return if (isLibxposed) {
                LspHookImpl.findClass(className!!, classLoader!!)
            } else {
                XposedHelpers.findClass(className, classLoader)
            }
        }

        fun findClassIfExists(className: String?, classLoader: ClassLoader?): Class<*>? {
            return if (isLibxposed) {
                LspHookImpl.findClassIfExists(className!!, classLoader!!)
            } else {
                XposedHelpers.findClassIfExists(className, classLoader)
            }
        }

        // ---- XposedBridge.hookMethod ----

        fun hookMethod(member: java.lang.reflect.Member, callback: XC_MethodHook): HookerBridge.UnhookHandle {
            return if (isLibxposed) {
                (bridge as LspHookImpl).hookXpMethod(member, callback)
            } else {
                XpBridgeUnhook(XposedBridge.hookMethod(member, callback))
            }
        }

        fun hookAllMethods(clazz: Class<*>, methodName: String?, callback: XC_MethodHook): Set<HookerBridge.UnhookHandle> {
            return if (isLibxposed) {
                val impl = bridge as LspHookImpl
                clazz.declaredMethods
                    .filter { it.name == methodName }
                    .map { impl.hookXpMethod(it, callback) }
                    .toSet()
            } else {
                XposedBridge.hookAllMethods(clazz, methodName, callback).map { XpBridgeUnhook(it) }.toSet()
            }
        }

        fun hookAllConstructors(clazz: Class<*>, callback: XC_MethodHook): Set<HookerBridge.UnhookHandle> {
            return if (isLibxposed) {
                val impl = bridge as LspHookImpl
                clazz.declaredConstructors
                    .map { impl.hookXpMethod(it, callback) }
                    .toSet()
            } else {
                XposedBridge.hookAllConstructors(clazz, callback).map { XpBridgeUnhook(it) }.toSet()
            }
        }

        // ---- XposedHelpers.findAndHook* ----

        fun findAndHookMethod(
            clazz: Class<*>, methodName: String?, vararg parameterTypesAndCallback: Any?
        ): HookerBridge.UnhookHandle {
            return if (isLibxposed) {
                findAndHookMethodLibxposed(clazz, methodName, *parameterTypesAndCallback)
            } else {
                XpBridgeUnhook(XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback))
            }
        }

        fun findAndHookConstructor(
            clazz: Class<*>, vararg parameterTypesAndCallback: Any?
        ): HookerBridge.UnhookHandle {
            return if (isLibxposed) {
                findAndHookConstructorLibxposed(clazz, *parameterTypesAndCallback)
            } else {
                XpBridgeUnhook(XposedHelpers.findAndHookConstructor(clazz, *parameterTypesAndCallback))
            }
        }

        // ---- invokeOriginalMethod ----

        fun invokeOriginalMethod(method: Method, thisObject: Any?, args: Array<Any?>): Any? {
            return bridge.invokeOriginalMethod(method, thisObject, args)
        }

        // ---- 日志 ----

        fun log(text: String) {
            bridge.log(text)
        }

        fun log(throwable: Throwable) {
            bridge.log(throwable)
        }

        // ---- libxposed 下的 findAndHookMethod 桥接 ----

        private fun findAndHookMethodLibxposed(
            clazz: Class<*>, methodName: String?, vararg parameterTypesAndCallback: Any?
        ): HookerBridge.UnhookHandle {
            val last = parameterTypesAndCallback.lastOrNull()
                ?: throw IllegalArgumentException("no callback provided")
            val types = parameterTypesAndCallback.dropLast(1).filterIsInstance<Class<*>>().toTypedArray()

            val method = findMethodWithTypes(clazz, methodName, *types)
            method.isAccessible = true

            val impl = bridge as LspHookImpl
            return when (last) {
                is XC_MethodReplacement -> impl.hookXpReplacement(method, last)
                is XC_MethodHook -> impl.hookXpMethod(method, last)
                else -> throw IllegalArgumentException("last argument must be XC_MethodHook or XC_MethodReplacement")
            }
        }

        private fun findAndHookConstructorLibxposed(
            clazz: Class<*>, vararg parameterTypesAndCallback: Any?
        ): HookerBridge.UnhookHandle {
            val last = parameterTypesAndCallback.lastOrNull()
                ?: throw IllegalArgumentException("no callback provided")
            val types = parameterTypesAndCallback.dropLast(1).filterIsInstance<Class<*>>().toTypedArray()

            val constructor = findConstructorWithTypes(clazz, *types)
            constructor.isAccessible = true

            val impl = bridge as LspHookImpl
            return when (last) {
                is XC_MethodReplacement -> impl.hookXpReplacement(constructor, last)
                is XC_MethodHook -> impl.hookXpMethod(constructor, last)
                else -> throw IllegalArgumentException("last argument must be XC_MethodHook or XC_MethodReplacement")
            }
        }

        /**
         * 按方法名和参数类型查找方法。
         */
        private fun findMethodWithTypes(clazz: Class<*>, methodName: String?, vararg paramTypes: Class<*>): Method {
            val methods = if (paramTypes.isEmpty()) {
                clazz.declaredMethods.filter { it.name == methodName }
            } else {
                clazz.declaredMethods.filter { method ->
                    if (method.name != methodName) return@filter false
                    if (method.parameterCount != paramTypes.size) return@filter false
                    paramTypes.indices.all { i ->
                        paramTypes[i].isAssignableFrom(method.parameterTypes[i])
                    }
                }
            }
            return methods.firstOrNull()
                ?: throw NoSuchMethodException("${clazz.name}.$methodName(${paramTypes.joinToString(", ") { it.simpleName ?: "?" }})")
        }

        private fun findConstructorWithTypes(clazz: Class<*>, vararg paramTypes: Class<*>): Constructor<*> {
            val ctors = if (paramTypes.isEmpty()) {
                clazz.declaredConstructors.toList()
            } else {
                clazz.declaredConstructors.filter { ctor ->
                    if (ctor.parameterCount != paramTypes.size) return@filter false
                    paramTypes.indices.all { i ->
                        paramTypes[i].isAssignableFrom(ctor.parameterTypes[i])
                    }
                }
            }
            return ctors.firstOrNull()
                ?: throw NoSuchMethodException("${clazz.name}.<init>(${paramTypes.joinToString(", ") { it.simpleName ?: "?" }})")
        }

        // ---- XpBridgeUnhook ----

        private class XpBridgeUnhook(private val delegate: XC_MethodHook.Unhook) : HookerBridge.UnhookHandle {
            override fun unhook() { delegate.unhook() }
        }
    }
}
