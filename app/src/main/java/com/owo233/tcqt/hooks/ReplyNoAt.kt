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
        val clazz = XpClassLoader.load("com.tencent.mvi.base.route.VMMessenger")
            ?: error("ReplyNoAt: 找不到 VMMessenger 类.")

        val target = clazz.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE &&
                    it.isPublic &&
                    it.paramCount == 1 &&
                    it.parameterTypes[0] == MsgIntent::class.java
        } ?: error("ReplyNoAt: 找不到指定方法.")

        target.hookBeforeMethod { param ->
            val arg = param.args[0]
            if (arg !is InputAtMsgIntent.InsertAtMemberSpan) return@hookBeforeMethod

            /**
             * 虽然通过遍历堆栈来判断是否为回复引用可以快速修复长按头像无法使用艾特的问题。
             * 但是遍历堆栈是一个非常离谱的操作，坦率地说，堆栈检测的性能非常差且不稳定。
             * 寻找更精确的 Hook 点是更好的选择，但就目前来说，可以作为临时解决方案。
             */
            val keyword = "aio.input.reply"
            val trace = Throwable().stackTrace
            var isReply = false
            for (e in trace) {
                val name = e.className
                if (name.indexOf(keyword) != -1) {
                    isReply =true
                    break
                }
            }

            if (isReply) {
                param.result = Unit
            }
        }
    }

    override val key: String get() = GeneratedSettingList.REPLY_NO_AT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
