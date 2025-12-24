package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookAfterAllConstructors
import com.owo233.tcqt.utils.hookBeforeAllMethods

@RegisterAction
@RegisterSetting(
    key = "remove_emo_reply",
    name = "移除消息表情回应",
    type = SettingType.BOOLEAN,
    desc = "移除长按消息时出现的表情回应气泡菜单并隐藏消息底部的表情回应视图。",
    uiTab = "界面"
)
class RemoveEmoReply : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl")
            .hookBeforeAllMethods("getSeparateEmoReplyMenuView") { param ->
                param.result = null
            }

        loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.ui.MsgTailFrameLayout")
            .hookAfterAllConstructors { param ->
                val view = param.args.getOrNull(2) as? View ?: return@hookAfterAllConstructors
                if (view.javaClass.name.endsWith("BubbleLayoutCompatPress")) {
                    val targetView = param.thisObject as View
                    targetView.visibility = View.GONE
                }
            }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_EMO_REPLY
}
