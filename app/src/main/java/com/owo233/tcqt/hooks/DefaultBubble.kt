package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.tencent.qqnt.kernel.nativeinterface.VASMsgBubble
import de.robv.android.xposed.XposedBridge

@RegisterAction
@RegisterSetting(
    key = "default_bubble",
    name = "强制使用默认气泡",
    type = SettingType.BOOLEAN,
    desc = "使用默认气泡，让花里胡哨的气泡不那么花里胡哨。",
    uiOrder = 3
)
class DefaultBubble : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XposedBridge.hookAllConstructors(VASMsgBubble::class.java, afterHook {
            val v = it.thisObject as VASMsgBubble
            v.bubbleId = 0
            v.subBubbleId = 0
        })
    }

    override val key: String get() = GeneratedSettingList.DEFAULT_BUBBLE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
