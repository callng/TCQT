package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil
import com.tencent.qqnt.kernel.nativeinterface.ReplyMsgMainInfo

@RegisterAction
class RemoveReplyMsgCheck : IAction {

    override val name: String get() = "移除回复消息不存在限制"
    override val desc: String get() = "常见于解决‘转发的聊天记录中不包含该内容’的提示，允许查看回复消息。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            QQNTWrapperUtil.CppProxy::class.java.hookMethodAfter({
                name = "findSourceOfReplyMsgFrom"
                paramTypes = arrayOf(arrayList, ReplyMsgMainInfo::class.java)
            }) { param ->
                val result = param.result as Long
                if (result == 0L) {
                    param.result = 1L
                }
            }
        }
    }

    override val key: String get() = "remove_reply_msg_check"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
