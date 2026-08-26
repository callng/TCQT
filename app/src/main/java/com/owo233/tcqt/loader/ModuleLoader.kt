package com.owo233.tcqt.loader

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.StartupScheduler
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.loader.api.Unhook
import com.owo233.tcqt.loader.modern.ModernHookEngine
import com.owo233.tcqt.loader.zygisk.ZygiskHookEngine
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.dexkit.DexKitCache
import com.owo233.tcqt.utils.dexkit.DexKitFinder
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.allConstructors
import com.tencent.common.app.BaseApplicationImpl
import dalvik.system.BaseDexClassLoader
import io.fastkv.FastKV
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object ModuleLoader {

    private const val QFIX_PROXY_CLASS = "com.tencent.common.app.QFixApplicationImplProxy"
    private const val QFIX_IMPL_CLASS = "com.tencent.common.app.QFixApplicationImpl"
    private const val TINKER_LOADER_CLASS = "com.tencent.tinker.loader.TinkerLoader"
    private const val TINKER_TRY_LOAD_METHOD = "tryLoad"

    private val sLoaded = AtomicBoolean(false)

    private var isInit = AtomicBoolean(false)
    private var hasCapturedTinker = AtomicBoolean(false)

    fun initialize(
        hostClassLoader: ClassLoader,
        selfPath: String,
        packageName: String,
        processName: String
    ): Boolean {
        if (sLoaded.get()) return true

        if (!isHostClassLoaderReady(hostClassLoader)) {
            return false
        }

        HookSteps.initModulePath(selfPath)
        HookSteps.initHandleLoadPackage(processName, packageName)

        if (!nextInit(hostClassLoader)) {
            return false
        }

        sLoaded.set(true)
        return true
    }

    private fun isHostClassLoaderReady(
        classLoader: ClassLoader
    ): Boolean {
        if (classLoader === this.javaClass.classLoader) {
            return false
        }

        if (classLoader !is BaseDexClassLoader) {
            return false
        }

        return try {
            classLoader.loadClass(QFIX_PROXY_CLASS)
            true
        } catch (_: ClassNotFoundException) {
            try {
                classLoader.loadClass(QFIX_IMPL_CLASS)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        } catch (_: LinkageError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun nextInit(hostClassLoader: ClassLoader): Boolean {
        val classNames = listOf(QFIX_PROXY_CLASS, QFIX_IMPL_CLASS)

        for (className in classNames) {
            try {
                val clazz = hostClassLoader.loadClass(className)
                val method = clazz.getDeclaredMethod(
                    "attachBaseContext",
                    Context::class.java
                )
                hookQFixAttach(method)
                return true
            } catch (_: ClassNotFoundException) {
                // ?
            } catch (th: Throwable) {
                Log.e("nextInit Failure: $className", th)
            }
        }

        return false
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
                .loadClass(TINKER_LOADER_CLASS)
                .getDeclaredMethod(
                    TINKER_TRY_LOAD_METHOD,
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
                    installMainDispatcher()
                    val app = param.thisObject as Application
                    HookSteps.initContext(app)
                    System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

                    val cacheValid = DexKitCache.initCache()
                    val missingKeys = DexKitFinder.getMissingKeys()
                    val needDexKitFind = !cacheValid || missingKeys.isNotEmpty()

                    // 只同步安装 CRITICAL，其余全部交给 StartupScheduler 在
                    // onCreate 返回后分批后台安装，避免宿主白屏时间随功能数量线性增长
                    val proc = HookSteps.resolveActionProcess()
                    val plan = HookSteps.initStartup(app, proc, missingKeys)
                    StartupScheduler.schedule(app, proc, plan, needDexKitFind)
                }
            }
        } catch (th: Throwable) {
            Log.e("doRealStartup Failure", th)
        }
    }

    private fun installMainDispatcher() {
        if (HookEngineManager.engine !is ZygiskHookEngine) return
        if (!ProcUtil.isMain && !ProcUtil.isTool) return
        runCatching {
            kotlinx.coroutines.Dispatchers::class.java
                .getDeclaredMethod("getMain")
                .hookBefore { param ->
                    param.result = ModuleScope.mainDispatcher
                }
        }.onFailure {
            Log.e("hook Dispatchers.getMain failed", it)
        }
    }

    fun reload(state: Map<*, *>) {
        installMainDispatcher()
        HookSteps.initModulePath(state["moduleApkPath"] as String)
        HookSteps.initHandleLoadPackage(
            state["hostProcessName"] as String,
            state["hostAppPackageName"] as String
        )
        HookEnv.setHostClassLoader(state["hostClassLoader"] as ClassLoader)
        HookSteps.injectClassLoader(state["hostClassLoader"] as ClassLoader)
        HookSteps.initContext(state["hostApplication"] as Application)

        System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

        if (HookEngineManager.engine is ModernHookEngine && ProcUtil.isMain) {
            SyncUtils.runOnUiThread {
                val topActivity = QQInterfaces.topActivity
                val activityName = topActivity.javaClass.name
                if (activityName.contains("SettingActivity")) {
                    topActivity.recreate()
                }
            }
        }

        val cacheValid = DexKitCache.initCache()
        val missingKeys = DexKitFinder.getMissingKeys()
        val needDexKitFind = !cacheValid || missingKeys.isNotEmpty()

        val app = state["hostApplication"] as Application
        val proc = HookSteps.resolveActionProcess()
        val plan = HookSteps.initStartup(app, proc, missingKeys)
        StartupScheduler.schedule(app, proc, plan, needDexKitFind)
    }
}
