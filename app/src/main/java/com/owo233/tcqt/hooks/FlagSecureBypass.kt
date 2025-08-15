package com.owo233.tcqt.hooks

import android.content.Context
import android.view.Window
import android.view.WindowManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting

@RegisterAction
class FlagSecureBypass: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        Window::class.java.hookMethod("setFlags").before {
            val flag = it.args[0] as Int
            val mask = it.args[1] as Int
            if ((mask and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                it.args[0] = flag and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
        }

        Window::class.java.hookMethod("addFlags").before {
            val flag = it.args[0] as Int
            if ((flag and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                it.args[0] = flag and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
        }
    }

    override val name: String get() = "绕过 FLAG_SECURE"

    override val key: String get() = TCQTSetting.FLAG_SECURE_BYPASS

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
