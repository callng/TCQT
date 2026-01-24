package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord

@RegisterAction
@RegisterSetting(
    key = "default_vas_attrs",
    name = "净化聊天界面装扮",
    type = SettingType.BOOLEAN,
    desc = "默认禁用他人消息的个性化气泡、字体、QQ秀头像与头像挂件，若需保留特定项目（如头像挂件），请在下方勾选排除。",
    uiTab = "界面"
)
@RegisterSetting(
    key = "default_vas_attrs.type",
    name = "可选保留",
    type = SettingType.INT_MULTI,
    defaultValue = "0",
    options = "保留个性气泡|保留个性字体|保留头像挂件|保留QQ秀头像"
)
class DefaultVASAttributes : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val options = GeneratedSettingList.getInt(GeneratedSettingList.DEFAULT_VAS_ATTRS_TYPE)

        AIOMsgItem::class.asResolver()
            .firstMethod {
                name = "getMsgRecord"
                emptyParameters()
            }.self
            .hookAfterMethod { param ->
                val msgRecord = param.result as MsgRecord
                if (msgRecord.senderUin.toString() != QQInterfaces.currentUin) {
                    msgRecord.msgAttrs?.values?.forEach { u ->
                        u?.vasMsgInfo?.let { vasInfo ->

                            // 隐藏头像挂件
                            if ((options and (1 shl 2)) == 0) {
                                vasInfo.avatarPendantInfo?.pendantId = 0L
                                vasInfo.avatarPendantInfo?.pendantDiyInfoId = 0
                            }

                            // 强制默认气泡
                            if ((options and (1 shl 0)) == 0) {
                                vasInfo.bubbleInfo?.bubbleId = 0
                                vasInfo.bubbleInfo?.subBubbleId = 0
                            }

                            // 强制默认字体
                            if ((options and (1 shl 1)) == 0) {
                                vasInfo.vasFont?.fontId = 0
                                vasInfo.vasFont?.subFontId = 0L
                                vasInfo.vasFont?.magicFontType = 0
                            }
                        }
                    }
                }
            }

        if (options and (1 shl 3) == 0) {
            // 新版超级QQ秀 应该是在 9.2.35 版本或以上才有的
            "com.tencent.mobileqq.ai.avatar.api.impl.AIAvatarSwitchApiImpl".toHostClass()
                .asResolver()
                .optional()
                .firstMethodOrNull {
                    name = "isQQShowEnableForAIO"
                    parameters(Long::class, Int::class, Long::class)
                }?.self?.hookBeforeMethod { param ->
                    val uin = (param.args[2] as Long).toString()
                    if (uin != QQInterfaces.currentUin) {
                        param.result = false
                    }
                }
        }
    }

    override val key: String get() = GeneratedSettingList.DEFAULT_VAS_ATTRS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
