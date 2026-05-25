package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.isPublic
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
class RemoveEmoReply : IAction {

    override val name: String get() = "移除消息表情回应"
    override val desc: String get() = "移除长按消息时出现的表情回应气泡菜单并隐藏消息底部的表情回应视图。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl")
            .hookMethodBefore({
                name = if (requireMinQQVersion(QQVersion.QQ_9_1_70))
                    "getSeparateEmoReplyMenuView" else "getEmoReplyMenuView"
            }) { param ->
                param.result = null
            }

        loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent")
            .declaredMethods.first { m ->
                m.isPublic && m.paramCount == 3 && m.returnType == Void.TYPE
                        && m.parameterTypes[0] == Int::class.javaPrimitiveType
                        && m.parameterTypes[2] == List::class.java
            }
            .hookBefore { param -> param.result = null }
    }

    override val key: String get() = "remove_emo_reply"
}
