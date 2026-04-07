package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObjectByTypeOrNull
import com.tencent.mobileqq.aio.event.AIOMsgSendEvent
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mvi.base.route.MsgIntent
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
@RegisterSetting(
    key = "reply_no_at",
    name = "移除引用消息自动艾特",
    type = SettingType.BOOLEAN,
    desc = "引用消息时不添加艾特文本。",
    uiTab = "界面"
)
class ReplyNoAt : IAction, DexKitTask {

    override fun onRun(app: Application, process: ActionProcess) {
        requireClass("reply_no_at").findMethod {
            returnType = void
            paramTypes = arrayOf(MsgIntent::class.java)
        }.hookReplace { param ->
            if (param.args[0] !is AIOMsgSendEvent.MsgOnClickReplyEvent)
                return@hookReplace param.invokeOriginal()

            val aioMsgItem = param.args[0]?.getObjectByTypeOrNull<AIOMsgItem>()
                ?: return@hookReplace param.invokeOriginal()
            val senderUid = aioMsgItem.msgRecord.senderUid

            aioMsgItem.msgRecord.senderUid = ""
            param.invokeOriginal()
            aioMsgItem.msgRecord.senderUid = senderUid
        }
    }

    override val key: String get() = GeneratedSettingList.REPLY_NO_AT

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "reply_no_at" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.input.reply")
            matcher {
                methods {
                    add { name("onDestroy") }
                    add { returnType(Set::class.java) }
                }
            }
        }
    )
}
