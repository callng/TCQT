package com.owo233.tcqt.loader.modern

import android.app.Application
import android.content.pm.ApplicationInfo
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.loader.ModuleLoader
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.loader.legacy.LegacyHookEngine
import com.owo233.tcqt.utils.dexkit.DexKitCache
import com.owo233.tcqt.utils.dexkit.DexKitFinder
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.log.LogAndroid
import com.owo233.tcqt.utils.reflect.callMethod
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.setObject
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterfaceWrapper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class ModernHookEntry : XposedModule {

    private var processName = ""
    private var isApi100Fallback = false

    @Suppress("unused")
    constructor(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) {
        // 相当于调用 super(base, param)
        this.setObject("mBase", base, XposedInterfaceWrapper::class.java)
        processName = param.processName

        // 降级为 Legacy API
        isApi100Fallback = true

        if (HookEngineManager.isInitialized) return
        HookEngineManager.engine = LegacyHookEngine()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (isApi100Fallback) {
            val packageName = param.packageName
            val base = this.getObject("mBase", XposedInterfaceWrapper::class.java)
            val applicationInfo = base.callMethod("getApplicationInfo") as ApplicationInfo
            val hostClassLoader = param.callMethod("getClassLoader") as ClassLoader

            if (HostTypeEnum.contain(packageName) && param.isFirstPackage) {
                ModuleLoader.initialize(
                    hostClassLoader,
                    applicationInfo.sourceDir,
                    packageName,
                    processName
                )

                if (ProcUtil.isMain) {
                    Log.i("在 API 100 版本中加载模块, 已降级为 Legacy API")
                }
            }
        }
    }

    @Suppress("unused")
    constructor() : super()

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        this.processName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        if (HookEngineManager.isInitialized) return

        val packageName = param.packageName

        if (HostTypeEnum.contain(packageName) && param.isFirstPackage) {
            HookEngineManager.engine = ModernHookEngine(this)

            ModuleLoader.initialize(
                param.classLoader,
                moduleApplicationInfo.sourceDir,
                packageName,
                processName
            )
        }
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        val oldHandles = param.oldHookHandles
        val engine = ModernHookEngine(this, oldHandles)
        HookEngineManager.engine = engine

        val state = param.savedInstanceState as? Map<*, *>
        if (state == null) {
            Log.e("Hot reload failed: no saved state found!")
            super.onHotReloaded(param)
            return
        }

        val app = state["application"] as? Application
        val hostClassLoader = state["hostClassLoader"] as? ClassLoader
        val processName = state["processName"] as? String
        val packageName = state["hostAppPackageName"] as? String
        val hostApkPath = state["hostApkPath"] as? String
        val appName = state["appName"] as? String
        val versionName = state["versionName"] as? String
        val versionCode = state["versionCode"] as? Long
        val moduleDataPath = state["moduleDataPath"] as? String

        if (app != null && hostClassLoader != null && processName != null && packageName != null) {
            // Update the Thread Context ClassLoader of the main thread to the host classloader
            Thread.currentThread().contextClassLoader = hostClassLoader

            HookEnv.setApplication(app)
            HookEnv.setHostClassLoader(hostClassLoader)
            HookEnv.setHostAppContext(app)
            HookEnv.setProcessName(processName)
            HookEnv.setHostAppPackageName(packageName)
            
            // Set the new module APK path instead of the old one
            HookEnv.setModuleApkPath(moduleApplicationInfo.sourceDir)
            
            if (hostApkPath != null) HookEnv.setHostApkPath(hostApkPath)
            if (appName != null) HookEnv.setAppName(appName)
            if (versionName != null) HookEnv.setVersionName(versionName)
            if (versionCode != null) HookEnv.setVersionCode(versionCode)
            if (moduleDataPath != null) HookEnv.setModuleDataPath(moduleDataPath)

            // Inject the new classloader parent structure
            HookSteps.injectClassLoader(hostClassLoader)
            System.getProperties()["tcqt.module_class_loader"] = this.javaClass.classLoader

            // Force load Kotlin coroutines Main dispatcher for the new generation ClassLoader
            runCatching {
                val oldTCCL = Thread.currentThread().contextClassLoader
                Thread.currentThread().contextClassLoader = this.javaClass.classLoader
                try {
                    val mainDispatcher = kotlinx.coroutines.Dispatchers.Main
                    LogAndroid.i("Successfully pre-initialized coroutines Main dispatcher on hot reload: $mainDispatcher")
                } finally {
                    Thread.currentThread().contextClassLoader = oldTCCL
                }
            }.onFailure {
                Log.e("Failed to pre-initialize coroutines Main dispatcher on hot reload", it)
            }

            // Re-initialize stub activities with the new classloader
            com.owo233.tcqt.lifecycle.ParasiticActivity.initForStubActivity(app)

            // If the host settings page is currently open, recreate it on the main thread to refresh setting items and listeners
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching {
                    val topActivity = com.owo233.tcqt.internals.QQInterfaces.topActivity
                    val activityName = topActivity.javaClass.name
                    if (activityName.contains("QPublicFragmentActivity") || activityName.contains("SettingActivity")) {
                        topActivity.recreate()
                    }
                }
            }

            // Re-apply hooks using the registered Actions
            if (DexKitCache.initCache()) {
                HookSteps.initHooks(app)
            } else {
                HookSteps.initHooks(app, excludeDexKitTask = DexKitCache.cacheMap.isEmpty())
                DexKitFinder.doFind()
            }

            // Clean up any old hooks that were not replaced by the new engine
            engine.cleanUpOldHooks()

            Log.i("Hot reload completed")
        } else {
            Log.e("Hot reload failed: saved state is incomplete!")
            super.onHotReloaded(param)
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        Log.i("Hot reloading in ${ProcUtil.procName}")
        val state = HashMap<String, Any>()
        runCatching { state["application"] = HookEnv.application }
        runCatching { state["hostClassLoader"] = HookEnv.hostClassLoader }
        runCatching { state["processName"] = HookEnv.processName }
        runCatching { state["hostAppPackageName"] = HookEnv.hostAppPackageName }
        runCatching { state["hostApkPath"] = HookEnv.hostApkPath }
        runCatching { state["appName"] = HookEnv.appName }
        runCatching { state["versionName"] = HookEnv.versionName }
        runCatching { state["versionCode"] = HookEnv.versionCode }
        runCatching { state["moduleDataPath"] = HookEnv.moduleDataPath }
        param.setSavedInstanceState(state)
        return true
    }
}
