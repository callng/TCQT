package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.callMethod
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.reflect.getFields
import com.owo233.tcqt.utils.reflect.getMethods
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.msg.api.IMsgService
import java.lang.reflect.Field
import java.lang.reflect.Method

@RegisterAction
@RegisterSetting(
    key = "repeat_message",
    name = "复读机 +1",
    type = SettingType.BOOLEAN,
    desc = "人类的本质是什么？不支持修改+1图标，可选触发方式，默认200ms内重复点击触发。",
    uiTab = "界面"
)
@RegisterSetting(
    key = "repeat_message.type",
    name = "可选触发方式",
    type = SettingType.INT_MULTI,
    defaultValue = "0",
    options = "单击触发复读"
)
class RepeatMessage : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val options = GeneratedSettingList.getInt(GeneratedSettingList.REPEAT_MESSAGE_TYPE)

        val componentClz =
            loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.msgfollow.AIOMsgFollowComponent")

        val imageViewLazyField: Field =
            componentClz.getFields(false)
                .firstOrNull {
                    it.type.isInterface && it.type.name == "kotlin.Lazy"
                }
                ?.apply { isAccessible = true }
                ?: error("imageViewLazy field not found")

        val setRepeatMsgIconMethod: Method =
            componentClz.getMethods(false)
                .firstOrNull {
                    it.paramCount == 1 && it.parameterTypes[0] == Integer.TYPE
                }
                ?.apply { isAccessible = true }
                ?: error("setRepeatMsgIcon method not found")

        val plusOneMethod: Method =
            componentClz.getMethods(false)
                .firstOrNull {
                    it.paramCount == 3 &&
                            it.parameterTypes[0] == Integer.TYPE &&
                            it.parameterTypes[2] == List::class.java
                }
                ?: error("plusOne method not found")

        plusOneMethod.hookAfterMethod { param ->
            val hostObject = param.thisObject

            val imageView =
                (imageViewLazyField.get(hostObject)!!
                    .callMethod("getValue") as? ImageView)
                    ?: return@hookAfterMethod

            if (imageView.context.javaClass.name.contains("MultiForwardActivity")) {
                return@hookAfterMethod
            }

            val msgRecord = MsgRecordHelper.getMsgRecord(param.args[1])

            if (shouldDisableRepeat(msgRecord)) {
                return@hookAfterMethod
            }

            if (imageView.visibility != View.VISIBLE) {
                setRepeatMsgIconMethod.invoke(hostObject, View.VISIBLE)
            }

            imageView.setDoubleClickListener(
                (options and (1 shl 0)) != 0,
                200L
            ) {
                performRepeatMessage(msgRecord)
            }
        }
    }

    private fun performRepeatMessage(msg: MsgRecord) {
        val contact = ContactHelper.generateContactByUid(msg.chatType, msg.peerUid)

        if (contact !is MapleContact.PublicContact) {
            Log.e("repeat message failed: Host version too low, need 9.0.70+")
            return
        }

        val msgServer = QQInterfaces.msgService
        val msgIds = arrayListOf(msg.msgId)
        val attrMap = HashMap<Int, MsgAttributeInfo>()

        msgServer.getMsgsByMsgId(contact.inner, msgIds) { _, _, list ->
            if (list.isEmpty()) {
                Log.e("repeat message failed: MsgRecord isEmpty!")
                Toasts.error("无法获取消息,请重试")
                return@getMsgsByMsgId
            }

            if (list[0].elements[0].picElement != null) {
                msgServer.forwardMsg(
                    msgIds,
                    contact.inner,
                    arrayListOf(contact.inner),
                    attrMap
                ) { result, str, _ ->
                    if (result != 0) {
                        Log.e("repeat message failed: (type=${msg.msgType}, code=$result, msg=$str)")
                    }
                }
            } else {
                val iMsgServer = QRoute.api(IMsgService::class.java)
                iMsgServer.sendMsg(contact.inner, list[0].elements) { result, str ->
                    if (result != 0) {
                        Log.e("repeat message failed: (type=${msg.msgType}, code=$result, msg=$str)")
                    }
                }
            }
        }
    }

    private fun shouldDisableRepeat(msg: MsgRecord): Boolean {
        return msg.msgType == MsgConstant.KMSGTYPEWALLET
    }

    private fun View.setDoubleClickListener(
        isSingleClick: Boolean,
        interval: Long,
        action: () -> Unit
    ) {
        var lastClickTime: Long? = null

        setOnClickListener {
            if (isSingleClick) {
                action()
            } else {
                val now = System.currentTimeMillis()
                val last = lastClickTime

                if (last != null && now - last <= interval) {
                    action()
                    lastClickTime = null
                } else {
                    lastClickTime = now
                }
            }
        }
    }

    private object MsgRecordHelper {
        private val getMsgRecordMethod by lazy {
            loadOrThrow("com.tencent.mobileqq.aio.msg.AIOMsgItem")
                .getDeclaredMethod("getMsgRecord")
                .apply { isAccessible = true }
        }

        fun getMsgRecord(msgItem: Any): MsgRecord {
            return getMsgRecordMethod.invoke(msgItem) as MsgRecord
        }
    }

    override val key: String get() = GeneratedSettingList.REPEAT_MESSAGE
}
