package com.owo233.tcqt.features.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil
import com.tencent.qqnt.kernel.nativeinterface.ReplyMsgMainInfo

@RegisterAction
@RegisterSetting(
    key = "remove_reply_msg_check",
    name = "移除回复消息不存在限制",
    type = SettingType.BOOLEAN,
    desc = "常见于解决‘转发的聊天记录中不包含该内容’的提示，允许查看回复消息。",
    uiTab = "界面"
)
class RemoveReplyMsgCheck : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            QQNTWrapperUtil.CppProxy::class.java.hookAfterMethod(
                "findSourceOfReplyMsgFrom",
                ArrayList::class.java,
                ReplyMsgMainInfo::class.java
            ) { param ->
                val result = param.result as Long
                if (result == 0L) {
                    param.result = 1L
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_REPLY_MSG_CHECK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
