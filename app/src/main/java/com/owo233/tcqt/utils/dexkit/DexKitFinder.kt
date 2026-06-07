package com.owo233.tcqt.utils.dexkit

import android.os.Bundle
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.generated.GeneratedActionList
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.func.ModuleCommand
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

    fun doFind() {
        if (ProcUtil.isMain && initDexKit()) {
            showFindToast()
        }
    }

    private fun showFindToast() {
        "com.tencent.mobileqq.activity.SplashActivity".toHostClass()
            .getDeclaredMethod("doOnCreate", Bundle::class.java)
            .hookAfter {
                Toasts.info("开始查找混淆方法")
                startFind()
            }
    }

    private fun startFind() {
        ModuleScope.launchIO(TAG) {
            val tasks = GeneratedActionList.ACTIONS
                .filter { clazz -> DexKitTask::class.java.isAssignableFrom(clazz) }
                .mapNotNull { clazz ->
                    runCatching {
                        clazz.new() as? DexKitTask
                    }.getOrNull()
                }
                .toMutableList()

            val oldCache = DexKitCache.cacheMap.toMap()
            val newCache = mutableMapOf<String, String>()

            DexKitBridge.create(HookEnv.hostApkPath).use { bridge ->
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

    fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        getQueryMap().forEach { (name, query) ->
            when (query) {
                is FindClass -> {
                    val result = bridge.findClass(query).singleOrNull()
                    if (result != null) {
                        cache[name] = result.descriptor
                    } else {
                        Log.e("$name: No class found matching query")
                    }
                }

                is FindMethod -> {

                    val result = bridge.findMethod(query).singleOrNull()
                    if (result != null) {
                        cache[name] = result.descriptor
                    } else {
                        Log.e("$name: No method found matching query")
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
