package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.data.troop.TroopInfo

@RegisterAction
class AllowOpenBlockedGroup : IAction {

    override val key: String get() = "allow_open_blocked_group"
    override val name: String get() = "允许打开被封禁群组"
    override val desc: String get() = "解除被封禁群组无法进入聊天页面的限制，对TIM无效。"
    override val uiTab: String get() = "基础"

    override fun onInit(): Boolean {
        return !HookEnv.isTim()
    }

    override fun onRun(app: Application, process: ActionProcess) {
        TroopInfo::class.java.apply {
            findMethod {
                name = "isUnreadableBlock"
                returnType = boolean
            }.hookBefore { param ->
                param.result = false
            }
            findMethod {
                name = "isNeedInterceptOnBlockTroop"
                returnType = boolean
            }.hookBefore { param ->
                param.result = false
            }
        }
    }
}
