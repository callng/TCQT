package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.tencent.common.config.pad.DeviceType

@RegisterAction
class ForceTabletMode: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.common.config.pad.PadUtil")
            ?.declaredMethods?.first {
                it.returnType == DeviceType::class.java && it.parameterCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }?.hookMethod(afterHook {
                it.result = DeviceType.TABLET
            })
    }

    override val name: String get() = "强制平板模式"

    override val key: String get() = TCQTSetting.FORCE_TABLET_MODE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
