package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount
import com.tencent.mobileqq.aio.input.at.InputAtMsgIntent
import com.tencent.mvi.base.route.MsgIntent

@RegisterAction
@RegisterSetting(
    key = "reply_no_at",
    name = "回复信息不带@",
    type = SettingType.BOOLEAN,
    desc = "回复消息时不添加 @ 对方。",
    uiOrder = 21
)
class ReplyNoAt : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mvi.base.route.VMMessenger")!!
            .declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                        method.isPublic && method.paramCount == 1 &&
                        method.parameterTypes[0] == MsgIntent::class.java
            }!!.hookBeforeMethod {param ->
                if (param.args[0] is InputAtMsgIntent.InsertAtMemberSpan) {
                    param.result = Unit
                }
            }
    }

    override val key: String get() = GeneratedSettingList.REPLY_NO_AT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
