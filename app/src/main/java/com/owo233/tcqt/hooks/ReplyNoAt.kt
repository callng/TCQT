package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.tencent.mobileqq.aio.msg.AIOMsgItem

@RegisterAction
class ReplyNoAt: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        FuzzyClassKit.findMethodByClassName(
            "com.tencent.mobileqq.aio.input.reply"
        ) { it.returnType == Void.TYPE && it.parameterTypes.size == 1
                    && it.parameterTypes[0] == AIOMsgItem::class.java
        }?.hookMethod(replaceHook {
            return@replaceHook null
        })
    }

    override val name: String get() = "移除群回复消息添加@"

    override val key: String get() = TCQTSetting.REPLY_NO_AT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
