package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.isFinal
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.isStatic
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.replaceMethod

@RegisterAction
@RegisterSetting(
    key = "disable_reaction_limit",
    name = "禁止过滤反应表情",
    type = SettingType.BOOLEAN,
    desc = "将更多的表情（Emoji）显示出来。",
    uiTab = "界面"
)
class DisableReactionLimit : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")?.let {
                it.replaceMethod("getFilterEmojiData") { null }
                it.replaceMethod("getFilterSysData") { null }
            }

            // 有意义吗？
            load("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.utils.a")
                ?.declaredMethods
                ?.single {
                    it.returnType == Long::class.javaPrimitiveType &&
                            it.paramCount == 0 && it.isPublic &&
                            it.isStatic && it.isFinal
                }?.hookAfterMethod { it.result = 0L }
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_REACTION_LIMIT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
