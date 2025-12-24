package com.owo233.tcqt.hooks.func.advanced

import android.content.Context
import android.view.Window
import android.view.WindowManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod


@RegisterAction
@RegisterSetting(
    key = "flag_secure_bypass",
    name = "绕过FLAG_SECURE",
    type = SettingType.BOOLEAN,
    desc = "绕过FlagSecure，允许截图和录屏。",
    uiTab = "高级"
)
class FlagSecureBypass : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        Window::class.java.hookBeforeMethod(
            "setFlags",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ) {
            val flag = it.args[0] as Int
            val mask = it.args[1] as Int
            if ((mask and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                it.args[0] = flag and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
        }

        Window::class.java.hookBeforeMethod(
            "addFlags",
            Int::class.javaPrimitiveType
        ) {
            val flag = it.args[0] as Int
            if ((flag and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                it.args[0] = flag and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
        }
    }

    override val key: String get() = GeneratedSettingList.FLAG_SECURE_BYPASS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
