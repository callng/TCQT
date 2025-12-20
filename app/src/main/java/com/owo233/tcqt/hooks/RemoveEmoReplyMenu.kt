package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookBeforeAllMethods

@RegisterAction
@RegisterSetting(
    key = "remove_emo_reply_menu",
    name = "移除消息表情表态",
    type = SettingType.BOOLEAN,
    desc = "移除长按消息时出现的表情表态菜单",
    uiTab = "界面"
)
class RemoveEmoReplyMenu : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl")
            .hookBeforeAllMethods("getSeparateEmoReplyMenuView") { param ->
                param.result = null
            }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_EMO_REPLY_MENU
}
