package com.owo233.tcqt.features.advanced

import android.view.Window
import android.view.WindowManager
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookMethodBefore

// r757：只 hook Window.setFlags/addFlags，只有 UI 进程有意义。原先 ALL 会让 MSF/TOOL
// 主线程白加载 android.view.Window（r756 实测在 MSF 里被拖到 20.4s）。
// QZONE 需要时再单独加 ActionProcess.QZONE，默认不加。回退：改回 ActionProcess.ALL。
@RegisterAction(processes = [ActionProcess.MAIN])
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
