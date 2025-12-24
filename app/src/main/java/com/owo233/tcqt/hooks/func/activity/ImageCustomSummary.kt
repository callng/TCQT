/**
 * 此 HOOK 来自 QAuxiliary
 * 选项部分直接复刻 QAuxiliary 选项
 * 由 owo233(callng) 完全手写一遍 表示尊重
 */

package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hookBeforeAllMethods
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

@RegisterAction
@RegisterSetting(
    key = "image_custom_summary",
    name = "自定义图片外显文字",
    type = SettingType.BOOLEAN,
    desc = "自定义消息列表中图片类型消息的外显文字。",
    uiTab = "界面"
)
@RegisterSetting(
    key = "image_custom_summary.type",
    name = "外显类型",
    type = SettingType.INT_MULTI,
    defaultValue = "0",
    options = "表情商城|表情泡泡|纯图片0/图文混排0|动画表情1/表情搜索2/表情消息4/表情推荐7"
)
@RegisterSetting(
    key = "image_custom_summary.string",
    name = "自定义内容",
    type = SettingType.STRING,
    textAreaPlaceholder = "填写内容, e.g: 你干嘛,哎哟,你好烦~"
)
class ImageCustomSummary : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val mHookType =
            GeneratedSettingList.getInt(GeneratedSettingList.IMAGE_CUSTOM_SUMMARY_TYPE)
        val mHookString =
            GeneratedSettingList.getString(GeneratedSettingList.IMAGE_CUSTOM_SUMMARY_STRING)

        if (mHookType == 0 || mHookString.isBlank()) {
            return
        }

        IKernelMsgService.CppProxy::class.java.hookBeforeAllMethods(
            "sendMsg" // 只有一个叫 sendMsg 的方法, 直接用 hookBeforeAllMethods
        ) { param ->
            val contact = param.args[1] as Contact
            val elements = param.args[2] as ArrayList<*>

            for (element in elements) {
                val msgElement = (element as MsgElement)

                msgElement.picElement?.let { picElement ->
                    val picSubType = picElement.picSubType

                    if ((mHookType and (1 shl 2)) != 0 && picSubType == 0) {
                        picElement.summary = mHookString
                    }
                    if ((mHookType and (1 shl 3)) != 0 && picSubType != 0) {
                        picElement.summary = mHookString
                        if (contact.chatType != 4) picElement.picSubType = 7
                    }
                }

                msgElement.marketFaceElement?.let { marketFaceElement ->
                    if ((mHookType and (1 shl 0)) != 0) {
                        marketFaceElement.faceName = mHookString
                    }
                }

                msgElement.faceBubbleElement?.let { faceBubbleElement ->
                    if ((mHookType and (1 shl 1)) != 0) {
                        faceBubbleElement.content = mHookString
                    }
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.IMAGE_CUSTOM_SUMMARY

    override fun canRun(): Boolean = PlatformTools.isNt() && GeneratedSettingList.getBoolean(key)
}
