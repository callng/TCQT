package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod

@RegisterAction
@RegisterSetting(
    key = "remove_qr_login_check",
    name = "移除扫码登录检查",
    type = SettingType.BOOLEAN,
    desc = "扫描相册里的二维码时不再拦截登录。",
    uiOrder = 22
)
class RemoveQRLoginCheck : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.open.agent.QrAgentLoginManager")
            ?.declaredMethods
            ?.firstOrNull {
                it.returnType == Void.TYPE && it.parameterTypes.size == 3 && it.parameterTypes[0] == Boolean::class.java
            }?.hookBeforeMethod {
                it.args[0] = false
            }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_QR_LOGIN_CHECK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
