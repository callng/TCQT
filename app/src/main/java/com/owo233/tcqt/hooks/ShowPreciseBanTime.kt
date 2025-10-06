package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList

@RegisterAction
@RegisterSetting(
    key = "show_precise_ban_time",
    name = "显示精准禁言时间",
    type = SettingType.BOOLEAN,
    desc = "禁言状态下在聊天页文字输入框中将替换显示精确的禁言时间，而非只显示单独的<天，分，秒>",
    uiOrder = 28
)
class ShowPreciseBanTime : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.qqnt.troop.impl.TroopGagUtils")!!
            .hookMethod("remainingTimeToStringCountDown", beforeHook {
                val time = it.args[0] as Long
                if (time <= 0) {
                    it.result = "0秒"
                } else {
                    it.result = formatDuration(time)
                }
            })
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}时")
            if (minutes > 0) append("${minutes}分")
            if (secs > 0 || isEmpty()) append("${secs}秒")
        }
    }

    override val key: String get() = GeneratedSettingList.SHOW_PRECISE_BAN_TIME
}
