package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
class DefaultVASAttributes : IAction {

    override val key: String get() = "default_vas_attrs"
    override val name: String get() = "净化聊天界面装扮"
    override val desc: String get() = "默认禁用他人消息的个性化气泡、字体、QQ秀头像与头像挂件，若需保留特定项目（如头像挂件），请在下方勾选排除。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                "default_vas_attrs.type",
                "可选保留",
                0,
                "",
                listOf("保留个性气泡", "保留个性字体", "保留头像挂件", "保留QQ秀头像")
            ),
        )

    override fun onInit(): Boolean {
        return HookEnv.isNT() && HookEnv.isQQ()
    }

    override fun onRun(app: Application, process: ActionProcess) {
        val options = TCQTSetting.getInt("default_vas_attrs.type")

        AIOMsgItem::class.java.findMethod {
            name = "getMsgRecord"
            paramCount = 0
        }.hookAfter { param ->
            val msgRecord = param.result as MsgRecord
            if (msgRecord.senderUin.toString() != QQInterfaces.currentUin) {
                msgRecord.msgAttrs?.values?.forEach { u ->
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
}
