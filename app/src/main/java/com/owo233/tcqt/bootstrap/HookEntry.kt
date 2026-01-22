package com.owo233.tcqt.bootstrap

import androidx.annotation.Keep
import com.owo233.tcqt.features.hooks.enums.HostTypeEnum
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookEntry: IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Keep
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (HostTypeEnum.contain(lpparam.packageName)) { //  && lpparam.isFirstApplication
            HookSteps.initHandleLoadPackage(lpparam)
            HookSteps.initLoad(lpparam)
        }
    }

    @Keep
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        HookSteps.initZygote(startupParam)
    }
}
