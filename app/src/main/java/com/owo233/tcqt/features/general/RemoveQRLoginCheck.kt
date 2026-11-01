package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.paramCount

@RegisterAction(processes = [ActionProcess.MAIN])
object RemoveQRLoginCheck : Feature(
    key = "remove_qr_login_check",
    name = "移除扫码登录检查",
    desc = "扫描相册里的二维码时不再拦截登录。",
) {


    override fun install() {
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

}
