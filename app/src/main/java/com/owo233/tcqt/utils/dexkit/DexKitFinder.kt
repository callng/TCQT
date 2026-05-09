package com.owo233.tcqt.utils.dexkit

import android.os.Bundle
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.generated.GeneratedActionList
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

internal object DexKitFinder {

    fun doFind() {
        if (initDexKit()) {
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

            DexKitBridge.create(HookEnv.hostApkPath).use { bridge ->
                tasks.forEach { task ->
                    runCatching {
                        task.getQueryMap().forEach { (name, query) ->
                            val tip = name

                            when (query) {
                                is FindClass -> {
                                    val result = bridge.findClass(query).singleOrNull()
                                    if (result != null) {
                                        DexKitCache.cacheMap[tip] = result.descriptor
                                    } else {
                                        Log.e("$tip: No class found matching query")
                                    }
                                }

                                is FindMethod -> {
                                    val result = bridge.findMethod(query).singleOrNull()
                                    if (result != null) {
                                        DexKitCache.cacheMap[tip] = result.descriptor
                                    } else {
                                        Log.e("$tip: No method found matching query")
                                    }
                                }
                            }
                        }
                    }.onFailure { Log.e("", it) }
                }
            }

            DexKitCache.saveCache()
            Toasts.success("查找完成，准备重启${HookEnv.appName}")
            delay(2500L)
            ModuleCommand.sendCommand(HookEnv.application, "exitApp")
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

    fun getQueryMap(): Map<String, BaseMatcher>

    fun requireClass(key: String): Class<*> {
        return DexKitCache.getClass(key)
    }

    fun requireMethod(key: String): Method {
        return DexKitCache.getMethod(key)
    }
}
