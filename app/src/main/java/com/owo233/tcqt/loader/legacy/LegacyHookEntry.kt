package com.owo233.tcqt.loader.legacy

import androidx.annotation.Keep
import com.owo233.tcqt.HookSteps
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.loader.ModuleLoader
import com.owo233.tcqt.loader.api.HookEngineManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class LegacyHookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private lateinit var modulePath: String

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (HostTypeEnum.contain(lpparam.packageName)) {
            if (HookEngineManager.isInitialized) return
            HookEngineManager.engine = LegacyHookEngine()

            HookSteps.initHandleLoadPackage(lpparam)
            ModuleLoader.initialize(
                lpparam.classLoader,
                modulePath,
                lpparam.packageName,
                lpparam.processName
            )
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }
}
