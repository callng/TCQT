package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.tencent.qqnt.kernel.nativeinterface.VASMsgFont
import de.robv.android.xposed.XposedBridge

@RegisterAction
@RegisterSetting(
    key = "default_font",
    name = "强制使用默认字体",
    type = SettingType.BOOLEAN,
    desc = "使用默认字体，让花里胡哨的字体不那么花里胡哨。",
    uiOrder = 4
)
class DefaultFont : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XposedBridge.hookAllConstructors(VASMsgFont::class.java, afterHook {
            val v = it.thisObject as VASMsgFont
            v.fontId = 0
            v.magicFontType = 0
        })
    }

    override val key: String get() = GeneratedSettingList.DEFAULT_FONT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
