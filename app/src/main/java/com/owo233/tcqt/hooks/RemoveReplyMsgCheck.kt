package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil

@RegisterAction
class RemoveReplyMsgCheck: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        QQNTWrapperUtil.CppProxy::class.java.hookMethod(
            "findSourceOfReplyMsgFrom",
            afterHook { param ->
                val result = param.result as Long
                if (result == 0L) {
                    param.result = 1L
                }
            }
        )
    }

    override val name: String get() = "移除回复消息不存在限制"

    override val key: String get() = TCQTSetting.REMOVE_REPLY_MSG_CHECK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
