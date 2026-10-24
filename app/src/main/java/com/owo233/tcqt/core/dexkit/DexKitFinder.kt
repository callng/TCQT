package com.owo233.tcqt.core.dexkit

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.reflect.TAG
import com.owo233.tcqt.core.reflect.new
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.generated.GeneratedActionList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ensureActive
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object DexKitFinder {

    private var resumeCallback: Application.ActivityLifecycleCallbacks? = null
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
    ) {
        if (ProcUtil.isMain && initDexKit()) {
            ModuleScope.launchMain {
                waitForHostResume(classLoader)
            }
        }
    }

    fun needsFind(): Boolean {
        val keys = getAllTaskKeys()
        return getMissingKeys().isNotEmpty() || DexKitCache.cacheMap.keys.any { it !in keys }
    }

    fun getMissingKeys(): Set<String> {
        val allKeys = getAllTaskKeys()
        if (!DexKitCache.isHostVersionMatched || !DexKitCache.isHostCodeMatched) {
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
        return allTasks.filter { task ->
            val keys = task.getCacheKeys()
            keys.isNotEmpty() && (missingKeys == null || keys.any { it in missingKeys })
        }
    }

    private fun waitForHostResume(classLoader: ClassLoader) {
        if (findStarted.get() || resumeCallback != null) return
        val app = HookEnv.application
        resumeCallback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                startOnce(classLoader)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }.also(app::registerActivityLifecycleCallbacks)
        // Modern 模块热重载时宿主可能已经在前台，不再等一次已经发生的首页 onResume。
        HostBridge.topActivity()?.takeIf {
            !it.isFinishing && !it.isDestroyed && it.window.decorView.isAttachedToWindow
        }?.let { startOnce(classLoader) }
    }

    private fun startOnce(classLoader: ClassLoader) {
        if (!findStarted.compareAndSet(false, true)) return
        resumeCallback?.let(HookEnv.application::unregisterActivityLifecycleCallbacks)
        resumeCallback = null
        startFind(classLoader)
    }

    private fun startFind(classLoader: ClassLoader) {
        ModuleScope.launchIO(TAG) {
            val snapshot = DexKitCache.snapshotForScan()
            val partialFind = snapshot.versionMatched

            val tasks = if (partialFind) {
                getTasks(getMissingKeys())
            } else {
                getTasks(null)
            }

            val oldCache = snapshot.values
            val allKeys = getAllTaskKeys()
            // 全量重查不能复用旧描述符或把旧 key 标记为已成功，否则会跳过热补丁中的变化。
            val newCache = if (partialFind) {
                oldCache.filterKeys { it in allKeys }.toMutableMap()
            } else {
                allKeys.associateWith { "" }.toMutableMap()
            }

            val scanKeys = tasks.flatMap { it.getCacheKeys() }.toSet().filterTo(mutableSetOf()) {
                !partialFind || newCache[it].isNullOrEmpty()
            }
            // Shared keys affect every declaring feature, including owners not executed
            // in this incremental scan. Read Feature metadata through the core interface.
            val ownersByKey = allTasks.flatMap { task ->
                task.getCacheKeys().map { key -> key to task }
            }.groupBy({ it.first }, { it.second })
            val featureNamesByKey = ownersByKey.mapValues { (_, owners) ->
                owners.map { (it as? ActionSpec)?.name ?: it.javaClass.name }.distinct()
            }
            Log.i("DexKit 开始${if (partialFind) "增量" else "全量"}扫描: ${snapshot.name}, ${scanKeys.size} 项")
            val state = MutableStateFlow(DexKitScanState(total = scanKeys.size))
            val progress = DexKitScanProgress(scanKeys, featureNamesByKey) { update ->
                state.value = update
                if (update.finished) {
                    update.failedKeys.forEach { key ->
                        val owners = ownersByKey[key].orEmpty().joinToString { it.scanOwner }
                        LogUtils.xposedNoFilter.e("DexKit 扫描失败: key=$key, 关联功能=[$owners]")
                    }
                    LogUtils.xposedNoFilter.i(
                        "DexKit 扫描结束: ${snapshot.name}, 共 ${update.total} 项, " +
                            "成功 ${update.succeeded} 项, 失败 ${update.failed} 项" +
                            (update.errorMessage?.let { ", $it" } ?: ""),
                    )
                }
            }
            ModuleScope.onMain {
                HostBridge.showDexKitScan?.invoke(state) {
                    findStarted.set(false)
                }
            }

            val tracker = DexKitLookupTracker(progress::record)
            if (partialFind) {
                tracker.markAllSucceeded(newCache.filterValues { it.isNotEmpty() }.keys)
            }

            runCatching {
                if (tasks.isNotEmpty()) {
                    DexKitBridge.create(classLoader, true).use { bridge ->
                        tasks.forEach { task ->
                            coroutineContext.ensureActive()
                            runCatching { task.execute(bridge, newCache, tracker) }
                                .onFailure {
                                    LogUtils.xposedNoFilter.e(
                                        "DexKit 任务执行异常: 关联功能=${task.scanOwner}, " +
                                            "keys=${task.getCacheKeys().joinToString()}",
                                        it,
                                    )
                                }
                            // Include keys skipped by an early return or a task-level exception.
                            task.getCacheKeys().forEach(tracker::markCompleted)
                        }
                    }
                }
            }.onFailure {
                LogUtils.xposedNoFilter.e("create DexKitBridge failed: ${snapshot.name}", it)
            }.getOrElse {
                progress.finish(restartRequired = false, errorMessage = "扫描失败，已保留原缓存。请查看日志后重试。")
                return@launchIO
            }

            // Modern 热重载会取消旧模块的作用域；旧扫描不能在取消后覆盖新模块缓存。
            coroutineContext.ensureActive()
            // 自定义任务同样不能把本宿主未声明的 key 写进最终缓存。
            newCache.keys.retainAll(allKeys)
            runCatching {
                DexKitCache.saveCache(snapshot, newCache)
            }.onFailure {
                LogUtils.xposedNoFilter.e("save DexKit cache failed: ${snapshot.name}", it)
                progress.finish(restartRequired = false, errorMessage = "缓存保存失败，请查看日志后重试。")
            }.onSuccess { saved ->
                if (saved) {
                    progress.finish(
                        previousCache = oldCache, savedCache = newCache,
                        previousCacheUsable = snapshot.previousCacheUsable,
                    )
                } else {
                    progress.finish(restartRequired = false, errorMessage = "扫描期间缓存已被清空或切换，本次结果已丢弃，请重启宿主后重新扫描。")
                }
            }
        }
    }

    private fun initDexKit(): Boolean {
        val ok = NativeLibs.load("dexkit")
        if (!ok) Log.e("dexkit library failed to load")
        return ok
    }
}

private val DexKitTask.scanOwner: String
    get() = (this as? ActionSpec)?.let { "${it.name} (featureKey=${it.key})" } ?: javaClass.name

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

        val result = runCatching { bridge.query() }
            .onFailure {
                LogUtils.xposedNoFilter.e(
                    "DexKit 查询异常: key=$name, 关联功能=$scanOwner",
                    it,
                )
            }
        val descriptor = result.getOrNull()
        if (descriptor.isNullOrEmpty()) {
            if (result.isSuccess) {
                LogUtils.xposedNoFilter.e(
                    "DexKit 查询未返回描述符: key=$name, 关联功能=$scanOwner",
                )
            }
            cache[name] = ""
        } else {
            cache[name] = descriptor
            tracker.markSucceeded(name)
        }
        tracker.markCompleted(name)
    }

    /** 重写 execute 时必须同时重写 getCacheKeys。 */
    fun execute(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
        tracker: DexKitLookupTracker
    ) {
        activeQueries().forEach { (name, matcher) ->
            lookup(name, bridge, cache, tracker) {
                when (matcher) {
                    is FindClass -> {
                        val matches = findClass(matcher)
                        check(matches.size == 1) {
                            "期望唯一类匹配，实际 ${matches.size} 项，候选（最多 5 项）=" +
                                matches.take(5).joinToString { it.descriptor }
                        }
                        matches.single().descriptor
                    }
                    is FindMethod -> {
                        val matches = findMethod(matcher)
                        check(matches.size == 1) {
                            "期望唯一方法匹配，实际 ${matches.size} 项，候选（最多 5 项）=" +
                                matches.take(5).joinToString { it.descriptor }
                        }
                        matches.single().descriptor
                    }
                    else -> error("不支持的 matcher 类型: ${matcher.javaClass.name}")
                }
            }
        }
    }

    /** getCacheKeys is the host-specific contract, not the unfiltered catalogue of queries. */
    fun activeQueries(): Map<String, BaseMatcher> {
        val keys = getCacheKeys()
        if (keys.isEmpty()) return emptyMap()
        return getQueryMap().filterKeys { it in keys }
    }

    fun requireClass(key: String): Class<*> {
        return DexKitCache.getClass(key)
    }

    fun requireMethod(key: String): Method {
        return DexKitCache.getMethod(key)
    }
}
