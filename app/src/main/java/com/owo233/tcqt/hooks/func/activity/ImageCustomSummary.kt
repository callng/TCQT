/**
 * 此 HOOK 来自 QAuxiliary
 * 选项部分直接复刻 QAuxiliary 选项
 * 由 owo233(callng) 完全手写一遍 表示尊重
 */

package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

@RegisterAction
class ImageCustomSummary : IAction {

    override val name: String get() = "自定义图片外显文字"
    override val desc: String get() = "自定义消息列表中图片类型消息的外显文字。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                "image_custom_summary.type",
                "外显类型",
                0,
                "",
                listOf(
                    "表情商城",
                    "表情泡泡",
                    "纯图片0/图文混排0",
                    "动画表情1/表情搜索2/表情消息4/表情推荐7"
                )
            ),
            StringSetting(
                "image_custom_summary.string",
                "自定义内容",
                "",
                "",
                "填写内容, e.g: 你干嘛,哎哟,你好烦~",
                false
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) {
        val mHookType =
            TCQTSetting.getInt("image_custom_summary.type")
        val mHookString =
            TCQTSetting.getString("image_custom_summary.string")

        if (mHookType == 0 || mHookString.isBlank()) {
            return
        }

        IKernelMsgService.CppProxy::class.java.hookMethodBefore({
            name = "sendMsg"
        }) { param ->
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

    override val key: String get() = "image_custom_summary"

    override fun canRun(): Boolean = PlatformTools.isNt() && TCQTSetting.getBoolean(key)
}
