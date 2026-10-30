package com.owo233.tcqt.ui.parasitic

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.os.Build
import android.os.Handler
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.reflect.callMethod
import com.owo233.tcqt.core.reflect.callStaticMethod
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.getObjectOrNull
import com.owo233.tcqt.core.reflect.getStaticObject
import com.owo233.tcqt.core.reflect.setObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object ActivityProxyManager {

    @Synchronized
    fun install() {
        val thread = checkNotNull(
            "android.app.ActivityThread".toHostClass().callStaticMethod("currentActivityThread")
        ) { "ActivityThread is unavailable" }

        val instrumentation = ProxyChain.unwrap(thread.getObject("mInstrumentation"), ::isWrapper)
            as Instrumentation
        val handler = thread.getObject("mH") as Handler
        val callback = ProxyChain.unwrap(handler.getObjectOrNull("mCallback"), ::isWrapper)
            as Handler.Callback?

        // Singleton.get() 必须先执行，否则 Android 8.1 的 mInstance 可能尚未初始化。
        val services = buildList {
            add(prepareService(
                "android.app.ActivityManager",
                "IActivityManagerSingleton",
                "android.app.IActivityManager",
            ))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(prepareService(
                    "android.app.ActivityTaskManager",
                    "IActivityTaskManagerSingleton",
                    "android.app.IActivityTaskManager",
                ))
            }
        }

        // 所有反射查找/代理创建成功后才发布；任一步失败则恢复已经替换的字段。
        val replacements = buildList {
            add(Replacement(thread, "mInstrumentation", ProxyInstrumentation(instrumentation)))
            add(Replacement(handler, "mCallback", ActivityLaunchHandler(callback, thread)))
            services.forEach { (singleton, proxy) -> add(Replacement(singleton, "mInstance", proxy)) }
        }
        val installed = mutableListOf<Replacement>()
        try {
            replacements.forEach { replacement ->
                replacement.target.setObject(replacement.field, replacement.value)
                installed += replacement
            }
        } catch (failure: Throwable) {
            installed.asReversed().forEach { replacement ->
                runCatching { replacement.target.setObject(replacement.field, replacement.previous) }
                    .exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
        }
    }

    private fun prepareService(owner: String, field: String, contract: String): Pair<Any, Any> {
        val singleton = checkNotNull(owner.toHostClass().getStaticObject(field))
        val original = checkNotNull(singleton.callMethod("get"))
        val base = checkNotNull(ProxyChain.unwrap(original, ::isWrapper))
        val serviceClass = contract.toHostClass()
        return singleton to Proxy.newProxyInstance(
            serviceClass.classLoader, arrayOf(serviceClass), ActivityManagerHandler(base),
        )
    }

    private fun isWrapper(value: Any): Boolean =
        value is Instrumentation || value is Handler.Callback || value is InvocationHandler

    private class Replacement(val target: Any, val field: String, val value: Any) {
        val previous: Any? = target.getObjectOrNull(field)
    }
}
