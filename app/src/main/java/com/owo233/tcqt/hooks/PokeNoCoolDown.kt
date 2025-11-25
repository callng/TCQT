package com.owo233.tcqt.hooks

import android.content.Context
import android.content.SharedPreferences
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.helper.MockSharedPreferences
import com.owo233.tcqt.utils.hookAfterMethod

@RegisterAction
@RegisterSetting(
    key = "poke_no_cool_down",
    name = "戳一戳无冷却",
    type = SettingType.BOOLEAN,
    desc = "移除戳一戳冷却时间(每天上限200次)。",
    uiOrder = 14
)
class PokeNoCoolDown : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        load("android.content.ContextWrapper")!!
            .hookAfterMethod(
                "getSharedPreferences",
                String::class.java,
                Int::class.javaPrimitiveType
            ) { param ->
                val name = param.args[0] as String
                if (name.startsWith("pai_yi_pai_user_double_tap_timestamp_")) {
                    if (param.result !is MockSharedPreferences) {
                        param.result = MockSharedPreferences(param.result as SharedPreferences)
                    }
                }
            }
    }

    override val key: String get() = GeneratedSettingList.POKE_NO_COOL_DOWN

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
