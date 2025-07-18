package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.tencent.qqnt.kernel.nativeinterface.VASMsgBubble
import de.robv.android.xposed.XposedBridge

@RegisterAction
class DefaultBubble: IAction {
    override fun onRun(ctx: Context) {
        XposedBridge.hookAllConstructors(VASMsgBubble::class.java, afterHook {
            val v = it.thisObject as VASMsgBubble
            v.bubbleId = 0
            v.subBubbleId = 0
        })
    }

    override val name: String get() = "强制使用默认气泡"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
