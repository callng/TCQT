package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import android.view.Window
import android.view.WindowManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookMethodBefore

@RegisterAction
class FlagSecureBypass : IAction {

    override val name: String get() = "绕过FLAG_SECURE"
    override val desc: String get() = "绕过FlagSecure，允许截图和录屏。"
    override val uiTab: String get() = "高级"

    override fun onRun(app: Application, process: ActionProcess) {
        Window::class.java.hookMethodBefore(
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

        Window::class.java.hookMethodBefore(
            "addFlags",
            Int::class.javaPrimitiveType
        ) {
            val flag = it.args[0] as Int
            if ((flag and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                it.args[0] = flag and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
        }
    }

    override val key: String get() = "flag_secure_bypass"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
