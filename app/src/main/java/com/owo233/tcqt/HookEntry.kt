package com.owo233.tcqt

import androidx.annotation.Keep
import com.owo233.tcqt.hooks.enums.HostTypeEnum
import com.owo233.tcqt.xposed.HookerBridgeManager
import com.owo233.tcqt.xposed.XpHookImpl
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Keep
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 初始化 HookerBridge 为传统 Xposed API 实现
        if (!HookerBridgeManager.isInitialized) {
            HookerBridgeManager.init(XpHookImpl(HookSteps.moduleApkPath))
        }

        if (HostTypeEnum.contain(lpparam.packageName)) {
            HookSteps.initHandleLoadPackage(lpparam)
            HookSteps.initLoad()
        }
    }

    @Keep
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        HookSteps.initZygote(startupParam)
    }
}
