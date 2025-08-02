package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem

@RegisterAction
class ReplyNoAt: IAction {
    override fun onRun(ctx: Context) {
        FuzzyClassKit.findMethodByClassName(
            "com.tencent.mobileqq.aio.input.reply"
        ) { it.returnType == Void.TYPE && it.parameterTypes.size == 1
                    && it.parameterTypes[0] == AIOMsgItem::class.java
        }?.hookMethod(
            beforeHook { param ->
                param.result = Unit
            }
        )
    }

    override val name: String get() = "移除群回复消息添加@"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
