package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookBeforeAllMethods
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "remove_emo_reply",
    name = "移除消息表情回应",
    type = SettingType.BOOLEAN,
    desc = "移除长按消息时出现的表情回应气泡菜单并隐藏消息底部的表情回应视图。",
    uiTab = "界面"
)
class RemoveEmoReply : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl")
            .hookBeforeAllMethods("getSeparateEmoReplyMenuView") { param ->
                param.result = null
            }

        loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent")
            .declaredMethods.first { m ->
                m.isPublic && m.paramCount == 3 && m.returnType == Void.TYPE
                        && m.parameterTypes[0] == Int::class.javaPrimitiveType
                        && m.parameterTypes[2] == List::class.java
            }
            .hookBeforeMethod { param -> param.result = null }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_EMO_REPLY
}
