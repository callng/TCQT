package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
@RegisterSetting(
    key = "poke_no_cool_down",
    name = "戳一戳无冷却",
    type = SettingType.BOOLEAN,
    desc = "移除戳一戳冷却时间(每天上限200次)。",
    uiTab = "界面"
)
class PokeNoCoolDown : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        "com.tencent.mobileqq.paiyipai.PaiYiPaiHandler".toHostClass().findMethod {
            returnType = boolean
            visibility = private
            paramTypes = arrayOf(string)
            paramCount = 1
        }.hookBefore { param -> param.result = true }
    }

    override val key: String get() = GeneratedSettingList.POKE_NO_COOL_DOWN

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
