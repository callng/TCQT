package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hookAfterMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
@RegisterSetting(
    key = "default_vas_attrs",
    name = "净化聊天界面装扮",
    type = SettingType.BOOLEAN,
    desc = "禁用他人消息的个性化气泡、字体与头像挂件。",
    uiOrder = 3,
    uiTab = "界面"
)
class DefaultVASAttributes : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        AIOMsgItem::class.java.hookAfterMethod("getMsgRecord") { param ->
            val msgRecord = param.result as MsgRecord
            if (msgRecord.senderUin.toString() != QQInterfaces.currentUin) {
                msgRecord.msgAttrs?.values?.forEach { u ->
                    u?.vasMsgInfo?.let { vasInfo ->

                        // 隐藏头像挂件
                        vasInfo.avatarPendantInfo?.pendantId = 0L
                        vasInfo.avatarPendantInfo?.pendantDiyInfoId = 0

                        // 强制默认气泡
                        vasInfo.bubbleInfo?.bubbleId = 0
                        vasInfo.bubbleInfo?.subBubbleId = 0

                        // 强制默认字体
                        vasInfo.vasFont?.fontId = 0
                        vasInfo.vasFont?.subFontId = 0L
                        vasInfo.vasFont?.magicFontType = 0
                    }
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.DEFAULT_VAS_ATTRS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
