package com.owo233.tcqt.loader

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.loader.api.Unhook
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.dexkit.DexKitCache
import com.owo233.tcqt.utils.dexkit.DexKitFinder
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.log.LogAndroid
import com.owo233.tcqt.utils.reflect.allConstructors
import com.tencent.common.app.BaseApplicationImpl
import dalvik.system.BaseDexClassLoader
import io.fastkv.FastKV
import java.io.File
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

        if (errors.size >= 2) errors.forEach { (className, th) ->
            Log.e("nextInit Failure: $className", th)
        }
        errors.clear()
    }

    private fun hookQFixAttach(attach: Method) {
        val constructorUnhooks = mutableListOf<Unhook>()

        attach.apply {
            hookBefore {
                tryDisableHotPatchEarly(it)

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
            }

            hookAfter { param ->
                constructorUnhooks.forEach { it.unhook() }
                constructorUnhooks.clear()

                if (!hasCapturedTinker.get()) {
                    val context = param.args[0] as Context
                    doRealStartup(context.classLoader)
                }
            }
        }
    }

    @SuppressLint("SdCardPath")
    private fun tryDisableHotPatchEarly(param: MethodHookParam) {
        val context = param.args[0] as Context
        val appName = TCQTBuild.APP_NAME

        val oldPath = context.getExternalFilesDir(null)?.parentFile?.let {
            "${it.absolutePath}/$appName"
        } ?: "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/${context.packageName}/$appName"

        val newPath = (context.filesDir?.let { File(it, "5463306EE50FE3AA/$appName") }
            ?: File("/data/user/${Process.myUserHandle().hashCode()}/${context.packageName}/files/5463306EE50FE3AA/$appName"))
            .also { it.mkdirs() }
            .absolutePath

        // 将在后续的几个版本更新中移除迁移逻辑
        val settingPath = if (File(oldPath).exists()) {
            val kvOld = FastKV.Builder("$oldPath/global/setting", appName).build()
            val kvNew = FastKV.Builder("$newPath/global/setting", appName).build()
            kvNew.putBoolean("disable_hot_patch", kvOld.getBoolean("disable_hot_patch", false))
            oldPath
        } else newPath

        if (!FastKV.Builder("$settingPath/global/setting", appName).build()
                .getBoolean("disable_hot_patch", false)
        ) return

        try {
            val classLoader = param.thisObject.javaClass.classLoader!!
            val tryLoadMethod = classLoader
                .loadClass("com.tencent.tinker.loader.TinkerLoader")
                .getDeclaredMethod(
                    "tryLoad",
                    classLoader.loadClass("com.tencent.tinker.loader.app.TinkerApplication")
                )

            val stubException = object : UnsupportedOperationException("Fuck Tinker") {
                override fun fillInStackTrace() = this
            }

            tryLoadMethod.hookBefore {
                it.result = Intent().apply {
                    putExtra("intent_return_code", -3)
                    putExtra("intent_patch_exception", stubException)
                    putExtra("intent_patch_interpret_exception", stubException)
                }
            }
        } catch (th: Throwable) {
            Log.e("tryDisableHotPatchEarly failed", th)
        }
    }

    private fun doRealStartup(reClassLoader: ClassLoader) {
        if (isInit.get()) return
        HookEnv.setHostClassLoader(reClassLoader)
        HookSteps.injectClassLoader(reClassLoader)

        try {
            BaseApplicationImpl::class.java.getDeclaredMethod("onCreate").hookBefore { param ->
                if (isInit.compareAndSet(false, true)) {
                    val app = param.thisObject as Application
                    HookSteps.initContext(app)
                    System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

                    // Force load Kotlin coroutines Main dispatcher under the module ClassLoader
                    runCatching {
                        val oldTCCL = Thread.currentThread().contextClassLoader
                        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
                        try {
                            val mainDispatcher = kotlinx.coroutines.Dispatchers.Main
                            LogAndroid.i("Successfully pre-initialized coroutines Main dispatcher: $mainDispatcher")
                        } finally {
                            Thread.currentThread().contextClassLoader = oldTCCL
                        }
                    }.onFailure {
                        Log.e("Failed to pre-initialize coroutines Main dispatcher", it)
                    }

                    val cacheValid = DexKitCache.initCache()
                    val needFind = !cacheValid || DexKitFinder.needsFind()

                    if (!needFind) {
                        HookSteps.initHooks(app)
                    } else {
                        HookSteps.initHooks(app, missingDexKitKeys = DexKitFinder.getMissingKeys())
                        DexKitFinder.doFind()
                    }
                }
            }
        } catch (th: Throwable) {
            Log.e("doRealStartup Failure", th)
        }
    }

    fun reload(state: Map<*, *>) {
        HookSteps.initModulePath(state["moduleApkPath"] as String)
        HookSteps.initHandleLoadPackage(
            state["hostProcessName"] as String,
            state["hostAppPackageName"] as String
        )
        HookEnv.setHostClassLoader(state["hostClassLoader"] as ClassLoader)
        HookSteps.injectClassLoader(state["hostClassLoader"] as ClassLoader)
        HookSteps.initContext(state["hostApplication"] as Application)

        System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

        runCatching {
            val oldTCCL = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = this.javaClass.classLoader
            try {
                val mainDispatcher = kotlinx.coroutines.Dispatchers.Main
                LogAndroid.i("Successfully pre-initialized coroutines Main dispatcher: $mainDispatcher")
            } finally {
                Thread.currentThread().contextClassLoader = oldTCCL
            }
        }.onFailure {
            Log.e("Failed to pre-initialize coroutines Main dispatcher", it)
        }

        if (ProcUtil.isMain) {
            SyncUtils.runOnUiThread {
                val topActivity = QQInterfaces.topActivity
                val activityName = topActivity.javaClass.name
                if (activityName.contains("SettingActivity")) {
                    topActivity.recreate()
                }
            }
        }

        val cacheValid = DexKitCache.initCache()
        val needDexKitFind = !cacheValid || DexKitFinder.needsFind()

        if (!needDexKitFind) {
            HookSteps.initHooks(state["hostApplication"] as Application)
        } else {
            HookSteps.initHooks(
                state["hostApplication"] as Application,
                missingDexKitKeys = DexKitFinder.getMissingKeys()
            )
            DexKitFinder.doFind()
        }
    }
}
