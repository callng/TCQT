package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.tencent.qqnt.kernel.nativeinterface.VASMsgFont
import de.robv.android.xposed.XposedBridge

@RegisterAction
class DefaultFont: IAction {
    override fun onRun(ctx: Context) {
        XposedBridge.hookAllConstructors(VASMsgFont::class.java, afterHook {
            val v = it.thisObject as VASMsgFont
            v.fontId = 0
            v.magicFontType = 0
        })
    }

    override val name: String get() = "强制使用默认字体"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
