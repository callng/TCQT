package com.owo233.tcqt.features.chat

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.util.ConcurrentModificationException

@RegisterAction
object DefaultVASAttributes : Feature(
    key = "default_vas_attrs",
    name = "净化聊天界面装扮",
    desc = "默认禁用他人消息的个性化气泡、字体、QQ秀头像与头像挂件，若需保留特定项目（如头像挂件），请在下方勾选排除。",
    requires = Requires(ntOnly = true, host = Requires.Host.QQOnly),
) {

    private val options by multiIntOption(
        settingKey = "type",
        name = "可选保留",
        defaultValue = 0,
        options = listOf("保留个性气泡", "保留个性字体", "保留头像挂件", "保留QQ秀头像"),
    )

    override fun install() {
        AIOMsgItem::class.java.findMethod {
            name = "getMsgRecord"
            paramCount = 0
        }.hookAfter { param ->
            val msgRecord = param.result as MsgRecord
            if (msgRecord.senderUin.toString() != QQInterfaces.currentUin) {
                snapshotVasAttributes(msgRecord.msgAttrs).forEach { u ->
                    u?.vasMsgInfo?.let { vasInfo ->

                        // 隐藏头像挂件
                        if (options.isFlagEnabled(2).not()) {
                            vasInfo.avatarPendantInfo?.pendantId = 0L
                            vasInfo.avatarPendantInfo?.pendantDiyInfoId = 0
                        }

                        // 强制默认气泡
                        if (options.isFlagEnabled(0).not()) {
                            vasInfo.bubbleInfo?.bubbleId = 0
                            vasInfo.bubbleInfo?.subBubbleId = 0
                        }

                        // 强制默认字体
                        if (options.isFlagEnabled(1).not()) {
                            vasInfo.vasFont?.fontId = 0
                            vasInfo.vasFont?.subFontId = 0L
                            vasInfo.vasFont?.magicFontType = 0
                        }
                    }
                }
            }
        }

        if (options.isFlagEnabled(3).not()) {
            if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_27)) {
                "com.tencent.mobileqq.ai.avatar.api.impl.AIAvatarSwitchApiImpl".toClass
                    .findMethod {
                        name = "isQQShowEnableForAIO"
                        paramTypes(long, int, long)
                    }.hookBefore { param ->
                        val uin = (param.args[2] as Long).toString()
                        if (uin != QQInterfaces.currentUin) {
                            param.result = false
                        }
                    }
            }
        }
    }

    private fun snapshotVasAttributes(
        attributes: Map<Int, MsgAttributeInfo?>?,
    ): List<MsgAttributeInfo?> {
        if (attributes == null) return emptyList()
        repeat(2) {
            try {
                return ArrayList<MsgAttributeInfo?>(attributes.size).also { list ->
                    for (attribute in attributes.values) list.add(attribute)
                }
            } catch (_: ConcurrentModificationException) {
                // retry
            }
        }
        return emptyList()
    }
}
