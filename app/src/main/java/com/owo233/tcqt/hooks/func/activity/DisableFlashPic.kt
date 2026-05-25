package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinTimVersion
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.TIMVersion
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class DisableFlashPic : IAction {

    override val name: String get() = "将闪照视为正常图片"
    override val desc: String get() = "好友发送的闪照将作为正常图片显示并添加灰条提示。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (PlatformTools.isNt() || requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)) {
            AIOMsgItem::class.java.hookMethodAfter({
                name = "getMsgRecord"
            }) { param ->
                val msgRecord = param.result as MsgRecord
                val subMsgType = msgRecord.subMsgType // 位掩码（Bitmask）
                // 8192 (闪照标记) + 2 (图片基础类型) = 8194
                if ((subMsgType and 8192) != 0) { // 带有闪照属性
                    msgRecord.subMsgType = subMsgType and 8192.inv() // 移除闪照属性
                }
            }
        }
    }

    override val key: String get() = "disable_flash_pic"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
