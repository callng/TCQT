package com.owo233.tcqt.features.chat

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.isNotAbstract
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.CommonTabEmojiInfo
import com.tencent.qqnt.kernel.nativeinterface.EmojiPanelCategory
import com.tencent.qqnt.kernel.nativeinterface.SysEmoji
import com.tencent.qqnt.kernel.nativeinterface.SysEmojiGroup
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction(priority = ActionPriority.BACKGROUND)
object ShowHideEmoticon : Feature(
    key = "show_hide_emoticon",
    name = "显示隐藏表情",
    desc = "让隐藏或处于灰度中的表情强制显示到表情列表中。",
), DexKitTask {


    /** 只在打开表情面板时才会被调用，放到 BACKGROUND 错峰安装。 */

    override fun install() {
        forceGrayEmoticonsIntoPanels()

        "com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo".toClass
            .declaredMethods
            .filter { m -> m.returnType == Boolean::class.java && m.isNotAbstract }
            .onEach { it.hookBefore { param -> param.result = false } }

        SysEmoji::class.java.findMethod {
            name = "getIsHide"
        }.hookBefore { param ->
            val emoji = param.thisObject as SysEmoji
            emoji.isHide = false
        }

        CommonTabEmojiInfo::class.java.findMethod {
            name = "getIsHide"
        }.hookBefore { param ->
            val emoji = param.thisObject as CommonTabEmojiInfo
            emoji.isHide = false
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "QQSysFaceResImpl" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.emoticon.kernel")
            matcher {
                superClass("com.tencent.mobileqq.emoticon.QQSysFaceResImpl")
                methods {
                    add { name("parseConfigData") }
                }
            }
        }
    )

    private fun forceGrayEmoticonsIntoPanels() {
        $$"com.tencent.mobileqq.emoticon.QQSysFaceSwitcher$enableAddSingleDownloadSysFaceToCache$2".toClass.findMethod {
            name = "invoke"
            returnType = boolean
        }.hookBefore { it.result = true }

        val resInfoClass = "com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo".toClass
        val sysFaceResClass = "com.tencent.mobileqq.emoticon.QQSysFaceResImpl".toClass
        val orderListField = resInfoClass.findField { name = "mOrderList" }
        val extOrderListField = sysFaceResClass.findField { name = "mExtAniStickerOrderList" }

        requireClass("QQSysFaceResImpl").findMethod {
            returnType = void
            paramTypes(
                EmojiPanelCategory::class.java,
                SysEmojiGroup::class.java,
                arrayList,
                arrayList
            )
        }.hookBefore { param ->
            val category = param.args[0] as EmojiPanelCategory
            if (category != EmojiPanelCategory.OTHER_PANEL) return@hookBefore

            val groupName = (param.args[1] as SysEmojiGroup).groupName
            if (groupName.isNullOrEmpty()) {
                (param.args[1] as SysEmojiGroup).groupName = "最近新增"
            }

            val owner = param.thisObject
            param.args[2] = orderListField.get(owner)
            param.args[3] = extOrderListField.get(owner)
        }
    }
}
