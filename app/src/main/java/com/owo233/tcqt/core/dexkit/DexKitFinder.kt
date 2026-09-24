package com.owo233.tcqt.core.dexkit

import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.hook.Unhook
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.TAG
import com.owo233.tcqt.core.reflect.new
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.generated.GeneratedActionList
import kotlinx.coroutines.delay
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method
import kotlin.time.Duration.Companion.seconds

class DexKitLookupTracker {

    private val succeeded = mutableSetOf<String>()

    fun isSucceeded(name: String): Boolean = name in succeeded

    fun markSucceeded(name: String) {
        succeeded += name
    }

    fun markAllSucceeded(names: Collection<String>) {
        succeeded += names
    }
}

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
            val partialFind = DexKitCache.isVersionMatched

            val tasks = if (partialFind) {
                getTasks(getMissingKeys())
            } else {
                getTasks(null)
            }

            val oldCache = DexKitCache.cacheMap.toMap()
            val newCache = DexKitCache.cacheMap.toMutableMap()

            val tracker = DexKitLookupTracker()
            if (partialFind) {
                tracker.markAllSucceeded(newCache.filterValues { it.isNotEmpty() }.keys)
            }

            runCatching {
                DexKitBridge.create(HookEnv.hostClassLoader, true).use { bridge ->
                    tasks.forEach { task ->
                        runCatching { task.execute(bridge, newCache, tracker) }
                            .onFailure { Log.e("", it) }
                    }
                }
            }.onFailure {
                Log.e("create DexKitBridge failed", it)
            }

            val isIdentical = oldCache.isNotEmpty() && oldCache == newCache

            DexKitCache.cacheMap = newCache
            DexKitCache.saveCache()

            if (isIdentical) {
                Toasts.success("查找完成，缓存匹配，无需重启")
            } else {
                Toasts.success("查找完成，准备重启${HookEnv.appName}")
                delay(2.5.seconds)
                ModuleCommandBus.sendCommand(HookEnv.application, ModuleCommandBus.CMD_RESTART)
            }
        }
    }

    private fun initDexKit(): Boolean {
        val ok = NativeLibs.load("dexkit")
        if (!ok) Log.e("dexkit library failed to load")
        return ok
    }
}

interface DexKitTask {

    fun getQueryMap(): Map<String, BaseMatcher> = emptyMap()

    /** 通常与 execute 一起重写。 */
    fun getCacheKeys(): Set<String> = getQueryMap().keys

    /**
     * 自定义 execute 时的统一查找入口:
     * 自带同名去重判断 + 成功/失败落库,只有真正命中才 markSucceeded。
     */
    fun lookup(
        name: String,
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
        tracker: DexKitLookupTracker,
        query: DexKitBridge.() -> String?
    ) {
        if (tracker.isSucceeded(name)) return

        val descriptor = bridge.query()
        if (descriptor.isNullOrEmpty()) {
            Log.e("$name: No result found matching query")
            cache[name] = ""
        } else {
            cache[name] = descriptor
            tracker.markSucceeded(name)
        }
    }

    /** 重写 execute 时必须同时重写 getCacheKeys。 */
    fun execute(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
        tracker: DexKitLookupTracker
    ) {
        getQueryMap().forEach { (name, matcher) ->
            lookup(name, bridge, cache, tracker) {
                when (matcher) {
                    is FindClass -> findClass(matcher).singleOrNull()?.descriptor
                    is FindMethod -> findMethod(matcher).singleOrNull()?.descriptor
                    else -> {
                        Log.e("$name: unsupported matcher type: ${matcher.javaClass.name}")
                        null
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
