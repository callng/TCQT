package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterAllConstructors
import com.tencent.qqnt.kernel.nativeinterface.VASMsgAvatarPendant

@RegisterAction
@RegisterSetting(
    key = "default_avatar_pendant",
    name = "隐藏头像挂件",
    type = SettingType.BOOLEAN,
    desc = "对聊天页面的头像挂件进行隐藏。",
    uiOrder = 2,
    uiTab = "界面"
)
class DefaultAvatarPendant : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        VASMsgAvatarPendant::class.java.hookAfterAllConstructors {
            val v = it.thisObject as VASMsgAvatarPendant
            v.pendantId = 0L
            v.pendantDiyInfoId = 0
        }
    }

    override val key: String get() = GeneratedSettingList.DEFAULT_AVATAR_PENDANT
}
