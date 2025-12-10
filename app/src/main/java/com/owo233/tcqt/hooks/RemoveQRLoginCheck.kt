package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "remove_qr_login_check",
    name = "移除扫码登录检查",
    type = SettingType.BOOLEAN,
    desc = "扫描相册里的二维码时不再拦截登录。",
)
class RemoveQRLoginCheck : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val clazz = load("com.tencent.open.agent.QrAgentLoginManager")!!
        val methods = clazz.declaredMethods

        val target = methods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 3 && it.parameterTypes[0] == Boolean::class.java
        } ?: methods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 4 && it.parameterTypes[1] == Boolean::class.java
        } ?: error("RemoveQRLoginCheck: 未找到匹配的方法!!!")

        target.hookBeforeMethod { param ->
            param.args.forEachIndexed { index, arg ->
                if (arg is Boolean) {
                    param.args[index] = false
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_QR_LOGIN_CHECK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
