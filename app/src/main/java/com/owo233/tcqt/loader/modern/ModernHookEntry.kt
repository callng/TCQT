package com.owo233.tcqt.loader.modern

import android.content.pm.ApplicationInfo
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.loader.ModuleLoader
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.loader.legacy.LegacyHookEngine
import com.owo233.tcqt.utils.log.Log
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
}
