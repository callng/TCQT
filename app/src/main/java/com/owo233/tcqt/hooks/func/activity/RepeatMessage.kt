package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.ContactHelper
import com.owo233.tcqt.hooks.helper.CustomMenu
import com.owo233.tcqt.hooks.helper.OnMenuBuilder
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.MethodHookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.paramCount
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.callMethod
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
class RepeatMessage : IAction, OnMenuBuilder {

    override val name: String get() = "复读机 +1"
    override val desc: String get() = "人类的本质是什么？不支持修改+1图标，可选触发方式，默认200ms内重复点击触发。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                OPTIONS_KEY,
                "可选项",
                DISPLAY_ICON,
                "",
                listOf("单击触发复读", "显示图标", "显示长按菜单")
            ),
        )

    private val repeatOptions: Int by lazy { resolveRepeatOptions() }

    override val targetComponentTypes: Array<String>
        get() = if (showMenu) REPEAT_MENU_COMPONENTS else emptyArray()

    override fun onRun(app: Application, process: ActionProcess) {
        if (!showIcon) return

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

        plusOneMethod.hookAfter { param ->
            val hostObject = param.thisObject

            val imageView =
                (imageViewLazyField.get(hostObject)!!
                    .callMethod("getValue") as? ImageView)
                    ?: return@hookAfter

            if (imageView.context.javaClass.name.contains("MultiForwardActivity")) {
                return@hookAfter
            }

            val msgRecord = MsgRecordHelper.getMsgRecord(param.args[1]!!)

            if (shouldDisableRepeat(msgRecord)) {
                return@hookAfter
            }

            if (imageView.visibility != View.VISIBLE) {
                setRepeatMsgIconMethod.invoke(hostObject, View.VISIBLE)
            }

            imageView.setDoubleClickListener(
                repeatOptions.isFlagEnabled(OPTION_SINGLE_CLICK_INDEX),
                200L
            ) {
                performRepeatMessage(msgRecord)
            }
        }
    }

    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        if (!showMenu) return
        if (isInMultiForwardActivity()) return

        val msgRecord = MsgRecordHelper.getMsgRecord(msg)
        val item = CustomMenu.createItemIconNt(
            msg = msg,
            text = "+1",
            icon = R.drawable.ic_item_repeat_72dp,
            id = R.id.item_repeat,
            click = {
                if (shouldDisableRepeat(msgRecord)) {
                    Toasts.error("该消息不支持复读")
                } else {
                    performRepeatMessage(msgRecord)
                }
            }
        )

        @Suppress("UNCHECKED_CAST")
        (param.result as? MutableList<Any>)?.add(0, item)
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

    private val showIcon: Boolean
        get() = repeatOptions.isFlagEnabled(DISPLAY_ICON_INDEX)

    private val showMenu: Boolean
        get() = repeatOptions.isFlagEnabled(DISPLAY_MENU_INDEX)

    private fun isInMultiForwardActivity(): Boolean {
        return runCatching {
            QQInterfaces.topActivity.javaClass.name.contains("MultiForwardActivity")
        }.getOrDefault(false)
    }

    override val key: String get() = "repeat_message"

    private fun resolveRepeatOptions(): Int {
        if (TCQTSetting.containsKey(OPTIONS_KEY)) {
            return TCQTSetting.getInt(OPTIONS_KEY)
        }

        val legacySingleClick =
            TCQTSetting.getInt(LEGACY_TRIGGER_OPTIONS_KEY).isFlagEnabled(OPTION_SINGLE_CLICK_INDEX)
        return DISPLAY_ICON or if (legacySingleClick) OPTION_SINGLE_CLICK else 0
    }

    private companion object {
        const val OPTIONS_KEY = "repeat_message.options"
        const val LEGACY_TRIGGER_OPTIONS_KEY = "repeat_message.type"
        const val OPTION_SINGLE_CLICK_INDEX = 0
        const val DISPLAY_ICON_INDEX = 1
        const val DISPLAY_MENU_INDEX = 2
        const val OPTION_SINGLE_CLICK = 1 shl OPTION_SINGLE_CLICK_INDEX
        const val DISPLAY_ICON = 1 shl DISPLAY_ICON_INDEX
        val REPEAT_MENU_COMPONENTS = arrayOf(
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.video.AIOVideoContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.template.AIOTemplateMsgComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.reply.AIOReplyComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.multipci.AIOMultiPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.AIOMultifowardContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.markdown.AIORichContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.longmsg.AIOLongMsgContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.filtervideo.AIOLiveVideoContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOFileContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOOnlineFileContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.facebubble.AIOFaceBubbleContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.chain.ChainAniStickerContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOArkContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOCenterArkContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.anisticker.AIOAniStickerContentComponent",
            "com.tencent.mobileqq.aio.shop.AIOShopArkContentComponent",
        )
    }
}
