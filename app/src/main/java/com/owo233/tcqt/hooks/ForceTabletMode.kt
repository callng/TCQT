package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterMethod
import com.tencent.common.config.pad.DeviceType

@RegisterAction
@RegisterSetting(
    key = "force_tablet_mode",
    name = "强制平板模式",
    type = SettingType.BOOLEAN,
    desc = "唯一的好处就是手机平板双端在线，具体可以干嘛呢？",
    uiOrder = 14
)
class ForceTabletMode : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.common.config.pad.PadUtil")
            ?.declaredMethods?.first {
                it.returnType == DeviceType::class.java && it.parameterCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }?.hookAfterMethod{
                it.result = DeviceType.TABLET
            }
    }

    override val key: String get() = GeneratedSettingList.FORCE_TABLET_MODE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
