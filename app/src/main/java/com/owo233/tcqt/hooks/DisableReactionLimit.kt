package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.generated.GeneratedSettingList

@RegisterAction
@RegisterSetting(key = "disable_reaction_limit", name = "禁止过滤反应表情", type = SettingType.BOOLEAN, defaultValue = "false")
class DisableReactionLimit: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")
            ?.hookMethod("getFilterEmojiData", replaceHook {
                return@replaceHook null
            })

        XpClassLoader.load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")
            ?.hookMethod("getFilterSysData", replaceHook {
                return@replaceHook null
            })

        // 有意义吗？
        XpClassLoader.load("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.utils.a")
            ?.hookMethod("c", replaceHook {
                return@replaceHook 0L
            })
    }

    override val key: String get() = GeneratedSettingList.DISABLE_REACTION_LIMIT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
