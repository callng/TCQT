package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.invokeOriginalMethod
import com.owo233.tcqt.utils.reflect.ClassUtils
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.MethodUtils
import com.owo233.tcqt.utils.replaceMethod
import com.tencent.mobileqq.aio.event.AIOMsgSendEvent
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mvi.base.route.MsgIntent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
@RegisterSetting(
    key = "reply_no_at",
    name = "回复信息不带@",
    type = SettingType.BOOLEAN,
    desc = "回复消息时不添加 @ 对方。",
    uiTab = "界面"
)
class ReplyNoAt : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val targetClass = ClassUtils.create("com.tencent.mobileqq.aio.input.reply")
            .join(ClassUtils.JoinStyle.DOT)
            .whereClass { c ->
                val methods = c.declaredMethods
                methods.any { it.name == "onDestroy" } &&
                        methods.any { it.returnType == Set::class.java }
            }
            .findFirstClass() ?: error("回复信息不带@: 找不到符合hook条件的类.")

        val handleIntent = MethodUtils.create(targetClass)
            .returns(Void.TYPE)
            .paramCount(1)
            .params(MsgIntent::class.java)
            .findOrThrow()

        handleIntent.replaceMethod { param ->
            val event = param.args.getOrNull(0)
            if (event !is AIOMsgSendEvent.MsgOnClickReplyEvent) {
                return@replaceMethod param.invokeOriginalMethod()
            }

            val msgRecord: MsgRecord = runCatching {
                val aioMsgItem = FieldUtils.create(event)
                    .typed(AIOMsgItem::class.java)
                    .getValue() ?: return@runCatching null

                val rec = FieldUtils.create(aioMsgItem)
                    .typed(MsgRecord::class.java)
                    .inParent(AIOMsgItem::class.java)
                    .getValue() ?: return@runCatching null

                rec as MsgRecord
            }.getOrNull() ?: return@replaceMethod param.invokeOriginalMethod()

            val senderUid = msgRecord.senderUid
            try {
                FieldUtils.create(msgRecord).named("senderUid").setValue("")
                return@replaceMethod param.invokeOriginalMethod()
            } finally {
                FieldUtils.create(msgRecord).named("senderUid").setValue(senderUid)
            }
        }
    }

    override val key: String get() = GeneratedSettingList.REPLY_NO_AT
}
