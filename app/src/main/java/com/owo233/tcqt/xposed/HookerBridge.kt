package com.owo233.tcqt.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * 统一的 Hook 桥接接口。
 *
 * 所有 hook 操作都通过此接口分发到具体实现（传统 Xposed / libxposed 101），
 * 上层业务代码只需依赖此抽象即可。
 */
interface HookerBridge {

    /** hook 优先级常量（与 XposedCallback 一致） */
    companion object Priority {
        const val DEFAULT = 50
        const val LOWEST = Integer.MIN_VALUE
        const val HIGHEST = Integer.MAX_VALUE
    }

    /** Hook 回调 */
    interface HookCallback {
        fun beforeHookedMethod(param: HookParam)
        fun afterHookedMethod(param: HookParam)
    }

    /** Replace 回调 */
    fun interface ReplaceCallback {
        fun replaceHookedMethod(param: HookParam): Any?
    }

    /** 统一的 Hook 参数 */
    interface HookParam {
        val member: Member
        val thisObject: Any?
        val args: Array<Any?>
        val returnType: Class<*>?

        fun setResult(result: Any?)
        fun getResult(): Any?
        fun hasResult(): Boolean
        fun setThrowable(throwable: Throwable?)
        fun getThrowable(): Throwable?
    }

    /** 统一的 Unhook 句柄 */
    interface UnhookHandle {
        fun unhook()
    }

    /** Hook 一个 Member（Method 或 Constructor），返回可取消的句柄 */
    fun hookMember(member: Member, priority: Int, callback: HookCallback): UnhookHandle

    /** Replace 一个 Member（Method 或 Constructor），返回可取消的句柄 */
    fun replaceMember(member: Member, priority: Int, callback: ReplaceCallback): UnhookHandle

    /** Hook 一个 Class 的所有同名方法 */
    fun hookAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: HookCallback): Set<UnhookHandle>

    /** Replace 一个 Class 的所有同名方法 */
    fun replaceAllMethods(clazz: Class<*>, methodName: String, priority: Int, callback: ReplaceCallback): Set<UnhookHandle>

    /** Hook 一个 Class 的所有构造函数 */
    fun hookAllConstructors(clazz: Class<*>, priority: Int, callback: HookCallback): Set<UnhookHandle>

    /** Replace 一个 Class 的所有构造函数 */
    fun replaceAllConstructors(clazz: Class<*>, priority: Int, callback: ReplaceCallback): Set<UnhookHandle>

    /** 调用原始方法 */
    fun invokeOriginalMethod(method: Method, thisObject: Any?, args: Array<Any?>): Any?

    /** 调用原始构造函数（仅执行 <init>，不 new 对象） */
    fun invokeOriginalConstructor(constructor: Constructor<*>, thisObject: Any, args: Array<Any?>)

    /** 日志输出（桥接到框架的日志系统） */
    fun log(message: String)
    fun log(throwable: Throwable)

    /** 获取模块 APK 路径 */
    val modulePath: String

    /** 是否为 libxposed 环境 */
    val isLibxposed: Boolean
}
