package com.owo233.tcqt.loader

import android.app.Application
import android.content.Context
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.loader.api.Unhook
import com.owo233.tcqt.utils.dexkit.DexKitCache
import com.owo233.tcqt.utils.dexkit.DexKitFinder
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.allConstructors
import com.tencent.common.app.BaseApplicationImpl
import dalvik.system.BaseDexClassLoader
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object ModuleLoader {

    private var sLoaded = false

    private var isInit = AtomicBoolean(false)
    private var hasCapturedTinker = AtomicBoolean(false)

    fun initialize(
        hostClassLoader: ClassLoader,
        selfPath: String,
        packageName: String,
        processName: String
    ) {
        if (sLoaded) return

        HookSteps.initModulePath(selfPath)
        HookSteps.initHandleLoadPackage(processName, packageName)

        nextInit(hostClassLoader)

        sLoaded = true
    }

    private fun nextInit(hostClassLoader: ClassLoader) {
        val classNames = listOf(
            "com.tencent.common.app.QFixApplicationImplProxy",
            "com.tencent.common.app.QFixApplicationImpl"
        )
        val errors = mutableListOf<Pair<String, Throwable>>()

        for (className in classNames) {
            try {
                val clazz = hostClassLoader.loadClass(className)
                val method = clazz.getDeclaredMethod("attachBaseContext", Context::class.java)
                hookQFixAttach(method)
                return
            } catch (th: Throwable) {
                errors.add(className to th)
            }
        }

        errors.forEach { (className, th) ->
            Log.e("nextInit Failure: $className", th)
        }
        errors.clear()
    }

    private fun hookQFixAttach(attach: Method) {
        attach.hookReplace { chain ->
            val constructorUnhooks = mutableListOf<Unhook>()

            BaseDexClassLoader::class.java.allConstructors().forEach { ctor ->
                val unhook = ctor.hookAfter { param ->
                    val loader = param.thisObject as ClassLoader
                    val loaderStr = loader.toString()
                    if (loaderStr.contains(TCQTBuild.APP_ID)) return@hookAfter

                    if ((loaderStr.contains("com.tencent.") ||
                                loaderStr.contains("TinkerClassLoader") ||
                                loaderStr.contains("DelegateLastClassLoader"))
                        && !hasCapturedTinker.get()
                    ) {
                        hasCapturedTinker.set(true)
                        Log.d("捕获到热更新 ClassLoader： $loader")
                        doRealStartup(loader)
                    }
                }
                constructorUnhooks.add(unhook)
            }

            try {
                return@hookReplace chain.proceed()
            } finally {
                constructorUnhooks.forEach { it.unhook() }
                constructorUnhooks.clear()

                if (!hasCapturedTinker.get()) {
                    val context = chain.args[0] as Context
                    doRealStartup(context.classLoader)
                }
            }
        }
    }

    @Synchronized
    private fun doRealStartup(reClassLoader: ClassLoader) {
        if (isInit.get()) return
        HookEnv.setHostClassLoader(reClassLoader)
        HookSteps.injectClassLoader(reClassLoader)

        try {
            BaseApplicationImpl::class.java.getDeclaredMethod("onCreate").hookAfter { param ->
                if (isInit.compareAndSet(false, true)) {
                    val app = param.thisObject as Application
                    HookSteps.initContext(app)

                    if (DexKitCache.initCache()) {
                        HookSteps.initHooks(app)
                    } else {
                        HookSteps.initHooks(app, excludeDexKitTask = true)
                        DexKitFinder.doFind()
                    }
                }
            }
        } catch (th: Throwable) {
            Log.e("doRealStartup Failure", th)
        }
    }
}
