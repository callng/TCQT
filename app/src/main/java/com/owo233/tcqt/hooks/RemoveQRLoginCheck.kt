package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting

@RegisterAction
class RemoveQRLoginCheck: IAction {

    override fun onRun(ctx: Context) {
        XpClassLoader.load("com.tencent.open.agent.QrAgentLoginManager")
            ?.declaredMethods
            ?.firstOrNull {
                it.returnType == Void.TYPE && it.parameterTypes.size == 3 && it.parameterTypes[0] == Boolean::class.java
            }?.hookMethod(beforeHook {
                it.args[0] = false
            })
    }

    override val name: String get() = "移除扫码登录检查"

    override val key: String get() = TCQTSetting.REMOVE_QR_LOGIN_CHECK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
