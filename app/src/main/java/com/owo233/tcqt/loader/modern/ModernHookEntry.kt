package com.owo233.tcqt.loader.modern

import android.content.pm.ApplicationInfo
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.loader.legacy.LegacyHookEngine
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.invokeAs
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterfaceWrapper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class ModernHookEntry : XposedModule {

    private var processName = ""
    private var isApi100Fallback = false

    constructor(base: XposedInterface, param: XposedModuleInterface.ModuleLoadedParam) {
        // 相当于调用 super(base, param)
        FieldUtils.create(this::class.java)
            .named("mBase")
            .inParent(XposedInterfaceWrapper::class.java)
            .setValue(base)

        processName = param.processName

        // 降级为 Legacy API
        isApi100Fallback = true

        if (HookEngineManager.isInitialized) return
        HookEngineManager.engine = LegacyHookEngine()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (isApi100Fallback) {
            val packageName = param.packageName
            val base = FieldUtils.create(this::class.java)
                .named("mBase")
                .inParent(XposedInterfaceWrapper::class.java)
                .getValue()!!
            val applicationInfo = base.invokeAs<ApplicationInfo>("getApplicationInfo")

            if (HostTypeEnum.contain(packageName) && param.isFirstPackage) {
                HookSteps.initModulePath(applicationInfo.sourceDir)
                HookSteps.initHandleLoadPackage(processName, packageName)
                HookSteps.initLoad()

                Log.w("在 API 100 版本中加载模块, 已降级为 Legacy API")
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

            HookSteps.initModulePath(moduleApplicationInfo.sourceDir)
            HookSteps.initHandleLoadPackage(processName, packageName)
            HookSteps.initLoad()
        }
    }
}
