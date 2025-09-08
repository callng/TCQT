package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.tencent.mobileqq.aio.msg.AIOMsgItem

@RegisterAction
@RegisterSetting(
    key = "reply_no_at",
    name = "回复信息不带@",
    type = SettingType.BOOLEAN,
    desc = "回复消息时不添加 @ 对方。",
    uiOrder = 26
)
class ReplyNoAt : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        FuzzyClassKit.findMethodByClassName(
            "com.tencent.mobileqq.aio.input.reply"
        ) { it.returnType == Void.TYPE && it.parameterTypes.size == 1
                    && it.parameterTypes[0] == AIOMsgItem::class.java
        }?.hookMethod(replaceHook {
            return@replaceHook null
        })
    }

    override val key: String get() = GeneratedSettingList.REPLY_NO_AT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
