package com.owo233.tcqt.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.callbacks.XCallback
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * 基于 libxposed API 101 的 [HookerBridge] 实现。
 *
 * libxposed 101 使用**拦截器链模型**（类似 OkHttp Interceptor）：
 * - `module.hook(executable)` 返回 `HookBuilder`
 * - `builder.setPriority(n).intercept(hooker)` 设置 Hooker 并返回 `HookHandle`
 * - Hooker 的 `intercept(chain)` 方法中，调用 `chain.proceed()` 执行原始方法
 *
 * ### 兼容传统 Xposed API（XC_MethodHook / XC_MethodReplacement）
 *
 * libxposed 的 api-82 stub 中 [MethodHookParam] 的字段（method, thisObject, args, result,
 * throwable, returnEarly）虽然声明为 public，但 libxposed 运行时不会填充它们。
 * 因此我们用反射直接写入这些 stub 字段，使上层代码的 `param.result = xxx`（Kotlin 属性
 * 语法，实际是字段写入）和 `param.setResult(xxx)`（方法调用）都能正确工作。
 */
class LspHookImpl(
    private val module: XposedModule
) : HookerBridge {

    override val isLibxposed: Boolean = true

    override val modulePath: String
        get() = module.moduleApplicationInfo.sourceDir

    // --- UnhookHandle 包装 ---

    private class LspUnhookHandle(private val handle: XposedInterface.HookHandle) : HookerBridge.UnhookHandle {
        override fun unhook() { handle.unhook() }
    }

    // --- hook Member（HookerBridge.HookCallback 路径） ---

    override fun hookMember(member: Member, priority: Int, callback: HookerBridge.HookCallback): HookerBridge.UnhookHandle {
        require(member is Executable) { "member must be an Executable (Method or Constructor)" }
        val rt = (member as? Method)?.returnType

        val handle = module.hook(member).setPriority(priority).intercept { chain ->
            val hookParam = BridgeHookParam(chain, member, rt)
            try {
                callback.beforeHookedMethod(hookParam)
            } catch (e: Throwable) {
                throw e
            }

            val resultOrThrowable = hookParam.takeResultOrThrowable()
            when {
                resultOrThrowable.second != null -> throw resultOrThrowable.second!!
                hookParam.hasResult() -> resultOrThrowable.first
                else -> {
                    val proceedResult = chain.proceed()
                    hookParam.setResult(proceedResult)
                    callback.afterHookedMethod(hookParam)
                    if (hookParam.hasResult()) hookParam.getResult() else proceedResult
                }
            }
        }

        return LspUnhookHandle(handle)
    }

    // --- replace Member ---

    override fun replaceMember(member: Member, priority: Int, callback: HookerBridge.ReplaceCallback): HookerBridge.UnhookHandle {
        require(member is Executable) { "member must be an Executable (Method or Constructor)" }

        val handle = module.hook(member).setPriority(priority).intercept { chain ->
            val rt = (member as? Method)?.returnType
            val hookParam = BridgeHookParam(chain, member, rt)
            callback.replaceHookedMethod(hookParam)
        }

        return LspUnhookHandle(handle)
    }

    // --- hookAll / replaceAll ---

    override fun hookAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: HookerBridge.HookCallback): Set<HookerBridge.UnhookHandle> {
        return clazz.declaredMethods
            .filter { it.name == methodName }
            .map { hookMember(it, priority, callback) }
            .toSet()
    }

    override fun replaceAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: HookerBridge.ReplaceCallback): Set<HookerBridge.UnhookHandle> {
        return clazz.declaredMethods
            .filter { it.name == methodName }
            .map { replaceMember(it, priority, callback) }
            .toSet()
    }

    override fun hookAllConstructors(clazz: Class<*>, priority: Int, callback: HookerBridge.HookCallback): Set<HookerBridge.UnhookHandle> {
        return clazz.declaredConstructors
            .map { hookMember(it, priority, callback) }
            .toSet()
    }

    override fun replaceAllConstructors(clazz: Class<*>, priority: Int, callback: HookerBridge.ReplaceCallback): Set<HookerBridge.UnhookHandle> {
        return clazz.declaredConstructors
            .map { replaceMember(it, priority, callback) }
            .toSet()
    }

    // --- invokeOriginal ---

    @Suppress("UNCHECKED_CAST")
    override fun invokeOriginalMethod(method: Method, thisObject: Any?, args: Array<Any?>): Any? {
        val invoker = module.getInvoker(method) as XposedInterface.Invoker<XposedInterface.Invoker<*, Method>, Method>
        invoker.setType(XposedInterface.Invoker.Type.ORIGIN)
        return invoker.invoke(thisObject, *args)
    }

    override fun invokeOriginalConstructor(constructor: Constructor<*>, thisObject: Any, args: Array<Any?>) {
        @Suppress("UNCHECKED_CAST")
        val invoker = module.getInvoker(constructor as Constructor<Any>)
        invoker.setType(XposedInterface.Invoker.Type.ORIGIN)
        invoker.invoke(thisObject, *args)
    }

    // --- 日志 ---

    override fun log(message: String) {
        module.log(android.util.Log.INFO, "TCQT", message, null)
    }

    override fun log(throwable: Throwable) {
        val message = throwable.message ?: throwable.javaClass.simpleName
        module.log(android.util.Log.ERROR, "TCQT", message, throwable)
    }

    // --- BridgeHookParam: 将 libxposed Chain 包装为 HookerBridge.HookParam ---

    private class BridgeHookParam(
        private val chain: XposedInterface.Chain,
        private val _member: Member,
        private val _returnType: Class<*>?
    ) : HookerBridge.HookParam {

        override val member: Member get() = _member
        override val thisObject: Any? get() = chain.thisObject
        override val args: Array<Any?> get() = chain.args.toTypedArray()
        override val returnType: Class<*>? get() = _returnType

        private var _result: Any? = UNSET
        private var _throwable: Throwable? = null

        companion object {
            internal val UNSET = Any()
        }

        override fun setResult(result: Any?) { _result = result }
        override fun getResult(): Any? = if (_result !== UNSET) _result else null
        override fun hasResult(): Boolean = _result !== UNSET
        override fun setThrowable(throwable: Throwable?) { _throwable = throwable }
        override fun getThrowable(): Throwable? = _throwable

        fun takeResultOrThrowable(): Pair<Any?, Throwable?> {
            val r = if (_result !== UNSET) _result else null
            return Pair(r, _throwable)
        }
    }

    // =====================================================================
    //  XC_MethodHook / XC_MethodReplacement 兼容层
    // =====================================================================

    /**
     * 桥接 [XC_MethodHook] 到 libxposed 拦截器链。
     *
     * 关键：传统 Xposed 的 `beforeHookedMethod(param)` 中，上层代码通过
     * `param.result = x`（**字段直接写入**，Kotlin 属性语法）或 `param.setResult(x)`
     * 来短路方法执行。libxposed stub 的 [MethodHookParam] 字段是 public 的，
     * 因此 Kotlin 字段写入能生效。我们在 before 回调后通过反射读取
     * `returnEarly` 字段来判断是否需要短路。
     */
    fun hookXpMethod(member: Member, xpHook: XC_MethodHook): HookerBridge.UnhookHandle {
        require(member is Executable) { "member must be an Executable" }

        val handle = module.hook(member).setPriority(xpHook.priority).intercept { chain ->
            val param = createParam(member, chain)

            // ---- beforeHookedMethod ----
            XpMethodCache.beforeMethod.invoke(xpHook, param)

            val returnEarly = XpFieldCache.returnEarlyField.getBoolean(param)
            if (returnEarly) {
                val throwable = param.throwable
                if (throwable != null) throw throwable
                return@intercept param.result
            }

            // ---- 执行原始方法 ----
            // before 回调可能修改了 param.args（如修改参数值），必须传给 proceed
            val proceedResult = chain.proceed(param.args)
            XpFieldCache.resultField.set(param, proceedResult)
            XpFieldCache.returnEarlyField.setBoolean(param, true)

            // ---- afterHookedMethod ----
            try {
                XpMethodCache.afterMethod.invoke(xpHook, param)
            } catch (_: Throwable) {}

            // after 可能通过 param.result = xxx 修改了结果
            if (XpFieldCache.returnEarlyField.getBoolean(param)) param.result else proceedResult
        }

        return LspUnhookHandle(handle)
    }

    /**
     * 桥接 [XC_MethodReplacement] 到 libxposed 拦截器链。
     *
     * [XC_MethodReplacement.beforeHookedMethod] 是 **final** 的，其内部会：
     * 1. 调用 `replaceHookedMethod(param)` 获取替换结果
     * 2. 调用 `param.setResult(result)` 设置到 param
     * 3. 调用 `param.setThrowable(t)` 如果 replace 抛出异常
     *
     * 所以我们只需调用基类的 `beforeHookedMethod`，然后读取 `returnEarly` 字段即可。
     * 调用基类方法使用 [Method.invoke]，而 [XC_MethodHook.beforeHookedMethod] 的
     * 实际执行对象是 `xpHook` 本身（子类匿名对象的 super 调用），
     * 这通过 [XpMethodCache.beforeMethod] 已正确获取。
     */
    fun hookXpReplacement(member: Member, replacement: XC_MethodReplacement): HookerBridge.UnhookHandle {
        require(member is Executable) { "member must be an Executable" }

        val handle = module.hook(member).setPriority(replacement.priority).intercept { chain ->
            val param = createParam(member, chain)

            // 调用 beforeHookedMethod（XC_MethodReplacement 的 final 实现）
            XpMethodCache.beforeMethod.invoke(replacement, param)

            // replaceHookedMethod 的结果已通过 param.setResult 写入
            val returnEarly = XpFieldCache.returnEarlyField.getBoolean(param)
            if (returnEarly) {
                val throwable = param.throwable
                if (throwable != null) throw throwable
                return@intercept param.result
            }

            // 理论上不会走到这里（XC_MethodReplacement 总是 setResult），但保险起见
            chain.proceed()
        }

        return LspUnhookHandle(handle)
    }

    // --- Param 工厂 ---

    /**
     * 创建 [MethodHookParam] 实例并用反射填充字段。
     */
    private fun createParam(member: Member, chain: XposedInterface.Chain): MethodHookParam {
        val param = MethodHookParam::class.java.getDeclaredConstructor().apply {
            isAccessible = true
        }.newInstance()

        XpFieldCache.methodField.set(param, member)
        XpFieldCache.thisObjectField.set(param, chain.thisObject)
        XpFieldCache.argsField.set(param, chain.args.toTypedArray())
        // result / throwable / returnEarly 由 constructor / 默认值初始化即可

        return param
    }

    // --- 反射缓存 ---

    /**
     * 缓存 [MethodHookParam] 的 public 字段反射对象。
     * 这些字段在 api-82 stub 中声明为 public，在运行时可用。
     */
    private object XpFieldCache {
        val methodField: Field = MethodHookParam::class.java.getDeclaredField("method")
        val thisObjectField: Field = MethodHookParam::class.java.getDeclaredField("thisObject")
        val argsField: Field = MethodHookParam::class.java.getDeclaredField("args")
        val resultField: Field = MethodHookParam::class.java.getDeclaredField("result")
        val throwableField: Field = MethodHookParam::class.java.getDeclaredField("throwable")
        // returnEarly 在 api-82 中是 package-private 字段，需要DeclaredField + accessible
        val returnEarlyField: Field = MethodHookParam::class.java.getDeclaredField("returnEarly")

        init {
            // returnEarly 是 package-private，其他字段是 public，统一 setAccessible 确保兼容
            arrayOf(methodField, thisObjectField, argsField, resultField, throwableField, returnEarlyField)
                .forEach { it.isAccessible = true }
        }
    }

    /**
     * 缓存 [XC_MethodHook] 的 protected 方法和 [XCallback.priority] 字段。
     */
    private object XpMethodCache {
        val beforeMethod: Method = XC_MethodHook::class.java.getDeclaredMethod(
            "beforeHookedMethod", MethodHookParam::class.java
        ).apply { isAccessible = true }

        val afterMethod: Method = XC_MethodHook::class.java.getDeclaredMethod(
            "afterHookedMethod", MethodHookParam::class.java
        ).apply { isAccessible = true }
    }

    // --- 工具方法 ---

    companion object {
        fun findClass(className: String, classLoader: ClassLoader): Class<*> {
            return classLoader.loadClass(className)
        }

        fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
            return try { classLoader.loadClass(className) } catch (_: ClassNotFoundException) { null }
        }
    }
}
