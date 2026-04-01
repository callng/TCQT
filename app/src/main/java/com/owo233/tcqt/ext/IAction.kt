package com.owo233.tcqt.ext

import android.content.Context
import com.owo233.tcqt.ActionManager
import com.owo233.tcqt.HookEnv.application
import com.owo233.tcqt.HookEnv.isQQ
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.HookEnv.versionCode
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.new
import java.lang.reflect.InvocationTargetException

enum class ActionProcess {
    MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV,
    OTHER, ALL
}

interface IAction {

    val key: String

    val processes: Set<ActionProcess> get() = DEFAULT_PROCESSES

    operator fun invoke(ctx: Context, process: ActionProcess) {
        runCatching {
            if (!canRun()) return@runCatching
            if (!initOnce()) return@runCatching

            onRun(ctx, process)
        }.onFailure {
            Log.e("功能 [${ActionManager.resolve(this)}] 执行异常", it)
        }
    }

    fun onRun(ctx: Context, process: ActionProcess)

    fun canRun(): Boolean = runCatching {
        GeneratedSettingList.getBoolean(key)
    }.getOrElse { e ->
        Log.e("功能 [${ActionManager.resolve(this)}] 开关检查异常", e)
        false
    }

    /**
     * 初始化逻辑
     * @return true 表示继续执行后续函数，false 则拦截
     */
    fun initOnce(): Boolean = true

    companion object {
        val DEFAULT_PROCESSES = setOf(ActionProcess.MAIN)
    }
}

/**
 * 无视设置开关条件的 Action
 */
abstract class AlwaysRunAction : IAction {
    override val key: String = ""
    override val processes: Set<ActionProcess> = IAction.DEFAULT_PROCESSES
    override fun canRun(): Boolean = true
}

abstract class PluginHook : IAction {
    abstract val pluginID: String

    @Throws(Throwable::class)
    abstract fun startHook(classLoader: ClassLoader)

    override fun onRun(ctx: Context, process: ActionProcess) = Unit

    override fun initOnce(): Boolean {
        if (disablePluginHook) {
            Log.e("pluginHook Unsupported versions")
            return false
        }

        Log.i("startPluginHook: $pluginID")

        initPluginProxyIfNeeded()

        try {
            val classLoader =
                getOrCreateClassLoaderMethod.invoke(null, application, pluginID) as? ClassLoader
            if (classLoader != null) {
                startHook(classLoader)
            } else {
                Log.e("plugin classLoader is null for $pluginID")
            }
        } catch (e: InvocationTargetException) {
            Log.e("plugin InvocationTargetException", e.targetException ?: e)
        } catch (e: Exception) {
            Log.e("plugin getClassLoader error", e)
        }

        return false
    }

    private fun initPluginProxyIfNeeded() {
        try {
            val clazz = "com.tencent.mobileqq.pluginsdk.IPluginAdapterProxy".toHostClass()
            val mGetProxy = clazz.findMethod {
                name = "getProxy"
                isStatic = true
                returnType = clazz
            }.apply { isAccessible = true }

            if (mGetProxy.invoke(null) == null) { // 一般不会执行if里面的代码
                val mSetProxy = clazz.findMethod {
                    name = "setProxy"
                    isStatic = true
                    returnType = void
                }.apply { isAccessible = true }

                val proxyInstance = PROXY_IMPL_CLASSES.firstNotNullOfOrNull { load(it) }?.new()

                if (proxyInstance != null) {
                    mSetProxy.invoke(null, proxyInstance)
                } else {
                    Log.e("initPluginProxy failed: 无法找到匹配的 Proxy 实现类")
                }
            }
        } catch (e: Exception) {
            Log.e("initPluginProxy error", e)
        }
    }

    companion object {
        val disablePluginHook: Boolean
            get() = isQQ() && versionCode == 4056L

        private val getOrCreateClassLoaderMethod by lazy {
            "com.tencent.mobileqq.pluginsdk.PluginStatic".toHostClass().findMethod {
                name = "getOrCreateClassLoader"
                returnType = ClassLoader::class.java
                paramTypes(context, string)
            }.apply { isAccessible = true }
        }

        private val PROXY_IMPL_CLASSES = listOf( // 每个版本混淆都发生变化
            "cooperation.plugin.f",                 // 9.2.70
            "cooperation.plugin.c",                 // 8.9.70
            "cooperation.plugin.PluginAdapterImpl", // 8.8.50
            "bghq",                                 // 8.2.11 Play
            "bfdk",                                 // 8.2.6
            "avgk",                                 // TIM 3.5.6
            "avel"                                  // TIM 3.5.2
        )
    }
}
