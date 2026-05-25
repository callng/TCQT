package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodReplace
import com.owo233.tcqt.utils.hook.isFinal
import com.owo233.tcqt.utils.hook.isPublic
import com.owo233.tcqt.utils.hook.isStatic
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
class DisableReactionLimit : IAction {

    override val name: String get() = "禁止过滤反应表情"
    override val desc: String get() = "将更多的表情（Emoji）显示出来。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")?.let {
                it.hookMethodReplace({
                    name = "getFilterEmojiData"
                }) { null }
                it.hookMethodReplace({
                    name = "getFilterSysData"
                }) { null }
            }

            // 有意义吗？
            load("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.utils.a")
                ?.declaredMethods
                ?.single {
                    it.returnType == Long::class.javaPrimitiveType &&
                            it.paramCount == 0 && it.isPublic &&
                            it.isStatic && it.isFinal
                }?.hookBefore { it.result = 0L }
        }
    }

    override val key: String get() = "disable_reaction_limit"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
