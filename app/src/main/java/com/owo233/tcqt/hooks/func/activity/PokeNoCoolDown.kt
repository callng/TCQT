package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class PokeNoCoolDown : IAction {

    override val name: String get() = "戳一戳无冷却"
    override val desc: String get() = "移除戳一戳冷却时间(每天上限200次)。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.mobileqq.paiyipai.PaiYiPaiHandler".toHostClass().findMethod {
            returnType = boolean
            visibility = private
            paramTypes = arrayOf(string)
            paramCount = 1
        }.hookBefore { param -> param.result = true }
    }

    override val key: String get() = "poke_no_cool_down"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
