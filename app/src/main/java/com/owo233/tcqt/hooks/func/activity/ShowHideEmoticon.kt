package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.isNotAbstract
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.CommonTabEmojiInfo
import com.tencent.qqnt.kernel.nativeinterface.EmojiPanelCategory
import com.tencent.qqnt.kernel.nativeinterface.SysEmoji
import com.tencent.qqnt.kernel.nativeinterface.SysEmojiGroup
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class ShowHideEmoticon : IAction, DexKitTask {

    override val key: String get() = "show_hide_emoticon"
    override val name: String get() = "显示隐藏表情"
    override val desc: String get() = "让隐藏或处于灰度中的表情强制显示到表情列表中。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
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
