package com.owo233.tcqt.utils.dexkit

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.generated.GeneratedActionList
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.func.ModuleCommand
import com.owo233.tcqt.loader.api.Unhook
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.TAG
import com.owo233.tcqt.utils.reflect.new
import kotlinx.coroutines.delay
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method
import kotlin.time.Duration.Companion.seconds

internal object DexKitFinder {

    private var unhook: Unhook? = null

    private val allTasks: List<DexKitTask> by lazy {
        GeneratedActionList.ACTIONS
            .filter { DexKitTask::class.java.isAssignableFrom(it) }
            .mapNotNull { clazz ->
                runCatching { clazz.new() as? DexKitTask }.getOrNull()
            }
    }

    fun doFind() {
        if (ProcUtil.isMain && initDexKit()) {
            showFindToast()
        }
    }

    fun needsFind(): Boolean {
        return getMissingKeys().isNotEmpty()
    }

    fun getMissingKeys(): Set<String> {
        val allKeys = getAllTaskKeys()
        if (!DexKitCache.isHostVersionMatched) {
            return allKeys
        }
        //  || DexKitCache.cacheMap[it]?.isEmpty() == true
        return allKeys.filter { it !in DexKitCache.cacheMap }.toSet()
    }

    private fun getAllTaskKeys(): Set<String> {
        return allTasks
            .flatMap { it.getCacheKeys() }
            .toSet()
    }

    private fun getTasks(missingKeys: Set<String>? = null): List<DexKitTask> {
        return allTasks.let { allTasks ->
            if (missingKeys.isNullOrEmpty()) allTasks
            else allTasks.filter { task -> task.getCacheKeys().any { it in missingKeys } }
        }
    }

    private fun showFindToast() {
        "com.tencent.mobileqq.activity.home.MainFragment".toHostClass()
            .getDeclaredMethod("onResume")
            .hookAfter {
                Toasts.info("开始查找混淆方法")
                startFind()
            }.also { unhook = it }
    }

    private fun startFind() {
        unhook?.unhook().also { unhook = null }

        ModuleScope.launchIO(TAG) {
            val tasks = if (DexKitCache.isVersionMatched) {
                getTasks(getMissingKeys())
            } else {
                getTasks(null)
            }

            val oldCache = DexKitCache.cacheMap.toMap()
            val newCache = DexKitCache.cacheMap.toMutableMap()

            DexKitBridge.create(HookEnv.hostClassLoader, true).use { bridge ->
                tasks.forEach { task ->
                    runCatching {
                        task.execute(bridge, newCache)
                    }.onFailure { Log.e("", it) }
                }
            }

            val isIdentical = oldCache.isNotEmpty() && oldCache == newCache

            DexKitCache.cacheMap = newCache
            DexKitCache.saveCache()

            if (isIdentical) {
                Toasts.success("查找完成，缓存匹配，无需重启")
            } else {
                Toasts.success("查找完成，准备重启${HookEnv.appName}")
                delay(2.5.seconds)
                ModuleCommand.sendCommand(HookEnv.application, "restart")
            }
        }
    }

    private fun initDexKit(): Boolean {
        return runCatching {
            System.loadLibrary("dexkit")
        }.onFailure {
            Log.e("dexkit library failed to load", it)
        }.isSuccess
    }
}

interface DexKitTask {

    fun getQueryMap(): Map<String, BaseMatcher> = emptyMap()

    /**
     * 通常 getCacheKeys 和 execute 会同时重写
     */
    fun getCacheKeys(): Set<String> = getQueryMap().keys

    /**
     * 重写 execute 方法的同时 必须同时重写 getCacheKeys 方法
     */
    fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        getQueryMap().forEach { (name, query) ->
            when (query) {
                is FindClass -> {
                    val result = bridge.findClass(query).singleOrNull()
                    if (result != null) {
                        cache[name] = result.descriptor
                    } else {
                        Log.e("$name: No class found matching query")
                        cache[name] = ""
                    }
                }

                is FindMethod -> {
                    val result = bridge.findMethod(query).singleOrNull()
                    if (result != null) {
                        cache[name] = result.descriptor
                    } else {
                        Log.e("$name: No method found matching query")
                        cache[name] = ""
                    }
                }
            }
        }
    }

    fun requireClass(key: String): Class<*> {
        return DexKitCache.getClass(key)
    }

    fun requireMethod(key: String): Method {
        return DexKitCache.getMethod(key)
    }
}
