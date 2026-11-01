package com.owo233.tcqt.features.advanced

import android.view.Window
import android.view.WindowManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookMethodBefore

@RegisterAction(processes = [ActionProcess.ALL])
object FlagSecureBypass : Feature(
    key = "flag_secure_bypass",
    name = "绕过FLAG_SECURE",
    desc = "绕过FlagSecure，允许截图和录屏。",
) {


    override fun install() {
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

}
