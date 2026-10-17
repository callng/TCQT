package com.owo233.tcqt.core.dexkit

import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.env.HookEnv
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
import java.util.concurrent.atomic.AtomicBoolean
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
    private val findStarted = AtomicBoolean(false)

    private val allTasks: List<DexKitTask> by lazy {
        GeneratedActionList.ACTIONS
            .filter { DexKitTask::class.java.isAssignableFrom(it) }
            .mapNotNull { clazz ->
                runCatching { clazz.new() as? DexKitTask }.getOrNull()
            }
    }

    fun doFind(
        classLoader: ClassLoader = HookEnv.hostClassLoader,
        forceFullFind: Boolean = false,
    ) {
        if (ProcUtil.isMain && initDexKit()) {
            if (forceFullFind) {
                // 热更新重查不依赖首页 onResume，避免回调已经发生或首页类变化时漏查。
                if (!findStarted.compareAndSet(false, true)) return
                Log.i("检测到热更新，使用当前 ClassLoader 全量查找混淆类和方法")
                Toasts.info("热更新后重新查找混淆方法")
                startFind(classLoader, forceFullFind = true)
            } else {
                showFindToast(classLoader)
            }
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

    private fun showFindToast(classLoader: ClassLoader) {
        classLoader.loadClass("com.tencent.mobileqq.activity.home.MainFragment")
            .getDeclaredMethod("onResume")
            .hookAfter {
                if (!findStarted.compareAndSet(false, true)) return@hookAfter
                Toasts.info("开始查找混淆方法")
                startFind(classLoader, forceFullFind = false)
            }.also { unhook = it }
    }

    private fun startFind(classLoader: ClassLoader, forceFullFind: Boolean) {
        unhook?.unhook().also { unhook = null }

        ModuleScope.launchIO(TAG) {
            val partialFind = !forceFullFind && DexKitCache.isVersionMatched

            val tasks = if (partialFind) {
                getTasks(getMissingKeys())
            } else {
                getTasks(null)
            }

            val oldCache = DexKitCache.cacheMap.toMap()
            // 全量重查不能复用旧描述符或把旧 key 标记为已成功，否则会跳过热补丁中的变化。
            val newCache = if (partialFind) {
                oldCache.toMutableMap()
            } else {
                getAllTaskKeys().associateWith { "" }.toMutableMap()
            }

            val tracker = DexKitLookupTracker()
            if (partialFind) {
                tracker.markAllSucceeded(newCache.filterValues { it.isNotEmpty() }.keys)
            }

            runCatching {
                DexKitBridge.create(classLoader, true).use { bridge ->
                    tasks.forEach { task ->
                        runCatching { task.execute(bridge, newCache, tracker) }
                            .onFailure { Log.e("", it) }
                    }
                }
            }.onFailure {
                Log.e("create DexKitBridge failed", it)
            }.getOrElse {
                Toasts.error("查找失败，保留原缓存")
                findStarted.set(false)
                return@launchIO
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
