package com.owo233.tcqt.features.chat

import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.R
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.paramCount
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.callMethod
import com.owo233.tcqt.core.reflect.getFields
import com.owo233.tcqt.core.reflect.getMethods
import com.owo233.tcqt.features.internal.pipeline.OnMenuBuilder
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.ContactHelper
import com.owo233.tcqt.host.service.CustomMenu
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.msg.api.IMsgService
import java.lang.reflect.Field
import java.lang.reflect.Method

@RegisterAction
object RepeatMessage : Feature(
    key = "repeat_message",
    name = "复读机 +1",
    desc = "人类的本质是什么？不支持修改+1图标，可选触发方式，默认200ms内重复点击触发。",
), OnMenuBuilder {

    /** 装配顺序：排在 PttForward(100) 之后，决定加号菜单里的先后。 */
    override val decoratorOrder: Int = 200

    /**
     * 刻意不用 `by`：解析旧 key 兜底需要 `isSet()` / `value` 这些元数据。
     */
    private val options = multiIntOption(
        settingKey = "options",
        name = "可选项",
        defaultValue = DISPLAY_ICON,
        options = listOf("单击触发复读", "显示图标", "显示长按菜单"),
    )

    /** 解析后的选项掩码。 */
    private val repeatOptions: Int by lazy { resolveRepeatOptions() }

    override val targetComponentTypes: Array<String>
        get() = if (showMenu) REPEAT_MENU_COMPONENTS else emptyArray()

    override fun install() {
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

        val menuList = param.result as? List<*> ?: return
        param.result = listOf(item) + menuList
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
            QQInterfaces.topActivity?.javaClass?.name?.contains("MultiForwardActivity") == true
        }.getOrDefault(false)
    }

    /**
     * `repeat_message.options` 从未被写过时回退到旧 key `repeat_message.type`，
     * 只认得「单击触发复读」这一位。
     */
    private fun resolveRepeatOptions(): Int {
        if (options.isSet()) return options.value

        val legacySingleClick =
            TCQTSetting.getInt(LEGACY_TRIGGER_OPTIONS_KEY).isFlagEnabled(OPTION_SINGLE_CLICK_INDEX)
        return DISPLAY_ICON or if (legacySingleClick) OPTION_SINGLE_CLICK else 0
    }

    private const val LEGACY_TRIGGER_OPTIONS_KEY = "repeat_message.type"
    private const val OPTION_SINGLE_CLICK_INDEX = 0
    private const val DISPLAY_ICON_INDEX = 1
    private const val DISPLAY_MENU_INDEX = 2
    private const val OPTION_SINGLE_CLICK = 1 shl OPTION_SINGLE_CLICK_INDEX
    private const val DISPLAY_ICON = 1 shl DISPLAY_ICON_INDEX

    private val REPEAT_MENU_COMPONENTS = arrayOf(
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
