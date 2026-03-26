package com.owo233.tcqt.xposed

import androidx.annotation.Keep
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 101 的模块入口。
 *
 * 在 LSPosed 2.x+（使用 libxposed）下，
 * 框架会通过 `META-INF/xposed/java_init.list` 发现此类并实例化。
 *
 * 注意：XposedModule() 是无参构造，框架通过 `attachFramework()` 注入 XposedInterface。
 */
@Keep
class LspHookEntry : XposedModule() {

    @Keep
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HookerBridgeManager.init(LspHookImpl(this))
    }

    @Keep
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        //
    }

    @Keep
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!HostTypeEnum.contain(param.packageName)) return

        HookSteps.initHandleLoadPackageCompat(
            processName = param.packageName,
            packageName = param.packageName,
            classLoader = param.classLoader
        )

        HookSteps.initLoad()
    }
}
