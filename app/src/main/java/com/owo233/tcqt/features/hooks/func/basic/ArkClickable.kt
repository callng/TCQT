package com.owo233.tcqt.features.hooks.func.basic

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.load
import com.owo233.tcqt.foundation.utils.hookBeforeMethod

@RegisterAction
@RegisterSetting(
    key = "ark_clickable",
    name = "允许打开Ark消息",
    type = SettingType.BOOLEAN,
    desc = "仅TIM可用，绕过部分Ark卡片消息禁止访问（请到最新版本QQ使用）的限制。",
)
class ArkClickable : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isTim()) {
            load("com.tencent.mobileqq.aio.msglist.holder.component.ark.d")
                ?.hookBeforeMethod("a", String::class.java, String::class.java) {
                    it.result =  true
                }
        }
    }

    override val key: String get() = GeneratedSettingList.ARK_CLICKABLE
}
