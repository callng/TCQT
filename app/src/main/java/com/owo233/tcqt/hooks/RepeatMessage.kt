package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.callMethod
import com.owo233.tcqt.utils.getFields
import com.owo233.tcqt.utils.getMethods
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.paramCount
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.msg.api.IMsgService
import kotlin.random.Random

@RegisterAction
@RegisterSetting(
    key = "repeat_message",
    name = "复读机 +1",
    type = SettingType.BOOLEAN,
    desc = "人类的本质是什么？不支持修改+1图标，为了防止误触，需要在200ms内重复点击才能触发。",
    uiOrder = 20
)
class RepeatMessage : IAction {

    private var lastClickTime = 0L

    override fun onRun(ctx: Context, process: ActionProcess) {
        val componentClz = XpClassLoader.load(
            "com.tencent.mobileqq.aio.msglist.holder.component.msgfollow.AIOMsgFollowComponent"
        ) ?: error("AIOMsgFollowComponent not found")

        val imageViewLazyField = componentClz.getFields(false).firstOrNull {
            it.type.isInterface && it.type.name == "kotlin.Lazy"
        }?.apply { isAccessible = true } ?: error("imageViewLazy not found")

        val setRepeatMsgIconMethod = componentClz.getMethods(false).firstOrNull {
            it.paramCount == 1 && it.parameterTypes[0] == Integer.TYPE
        }?.apply { isAccessible = true } ?: error("setRepeatMsgIcon not found")

        val plusOneMethod = componentClz.getMethods(false).firstOrNull {
            it.paramCount == 3 &&
                    it.parameterTypes[0] == Integer.TYPE &&
                    it.parameterTypes[2] == List::class.java
        } ?: error("plusOne not found")

        plusOneMethod.hookAfterMethod {
            val imageView = imageViewLazyField.get(it.thisObject)!!.callMethod(
                "getValue"
            ) as ImageView

            if (imageView.context.javaClass.name.contains("MultiForwardActivity")) return@hookAfterMethod

            val msgObject = it.args[1]
            val msgRecord = getMsgRecord(msgObject)

            if (disableRepeat(msgRecord)) return@hookAfterMethod

            if (imageView.visibility != View.VISIBLE) {
                setRepeatMsgIconMethod.invoke(it.thisObject, View.VISIBLE)
            }

            imageView.setOnClickListener { _ ->
                val now = System.currentTimeMillis()
                if (now - lastClickTime <= 200) {
                    val contact = ContactHelper.generateContactByUid(msgRecord.chatType, msgRecord.peerUid)
                    val newMsgId = generateMsgUniSeq(msgRecord.chatType)
                    val msgService = QRoute.api(IMsgService::class.java)

                    when (contact) {
                        is MapleContact.PublicContact -> {
                            msgService.sendMsgWithMsgId(contact.inner, newMsgId, msgRecord.elements) { result, _ ->
                                if (result != 0) {
                                    Log.e("repeat message failed: (msgType = ${msgRecord.msgType}, result = $result)")
                                }
                            }
                        }
                        else -> {}
                    }
                    lastClickTime = 0L
                } else {
                    lastClickTime = now
                }
            }
        }
    }

    private fun disableRepeat(msg: MsgRecord): Boolean {
        return msg.msgType == MsgConstant.KMSGTYPEWALLET // 跟*有关系的消息有必要复读吗？
    }

    private fun getMsgRecord(msg: Any): MsgRecord {
        return getMsgRecordMethod.invoke(msg) as MsgRecord
    }

    private fun generateMsgUniSeq(chatType: Int): Long {
        val uniseq = (System.currentTimeMillis() / 1000) shl 32
        val random = Random.nextLong() and 0xffffff00L
        return uniseq or random or chatType.toLong()
    }

    companion object {
        private val getMsgRecordMethod by lazy {
            XpClassLoader.load("com.tencent.mobileqq.aio.msg.AIOMsgItem")!!
                .getDeclaredMethod("getMsgRecord").apply { isAccessible = true }
        }
    }

    override val key: String get() = GeneratedSettingList.REPEAT_MESSAGE
}
