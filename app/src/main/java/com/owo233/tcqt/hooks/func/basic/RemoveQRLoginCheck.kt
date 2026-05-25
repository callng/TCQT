package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
class RemoveQRLoginCheck : IAction {

    override val name: String get() = "移除扫码登录检查"
    override val desc: String get() = "扫描相册里的二维码时不再拦截登录。"
    override val uiTab: String get() = "基础"

    override fun onRun(app: Application, process: ActionProcess) {
        val clazz = load("com.tencent.open.agent.QrAgentLoginManager")!!
        val methods = clazz.declaredMethods

        val target = methods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 3 && it.parameterTypes[0] == Boolean::class.java
        } ?: methods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 4 && it.parameterTypes[1] == Boolean::class.java
        } ?: error("RemoveQRLoginCheck: 未找到匹配的方法!!!")

        target.hookBefore { param ->
            param.args.forEachIndexed { index, arg ->
                if (arg is Boolean) {
                    param.args[index] = false
                }
            }
        }
    }

    override val key: String get() = "remove_qr_login_check"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
