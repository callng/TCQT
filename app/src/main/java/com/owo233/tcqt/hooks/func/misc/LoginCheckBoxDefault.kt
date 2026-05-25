package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.allConstructors

@RegisterAction
class LoginCheckBoxDefault : IAction {

    override val name: String get() = "默认勾选登录协议"
    override val desc: String get() = "登录界面自动勾选复选框（用户协议，有人看了吗）。"
    override val uiTab: String get() = "杂项"

    override fun onRun(app: Application, process: ActionProcess) {
        CheckBox::class.java.allConstructors().forEach {
            it.hookAfter { param ->
                val context = param.args.getOrNull(0) as? Context ?: return@hookAfter
                val className = context.javaClass.name

                if (!loginContextNames.contains(className)) return@hookAfter

                val checkBox = param.thisObject as CheckBox

                if (!checkBox.isChecked) {
                    checkBox.post { checkBox.isChecked = true }
                }
            }
        }
    }

    companion object {
        private val loginContextNames = setOf(
            "com.tencent.mobileqq.activity.LoginActivity",
            "com.tencent.mobileqq.activity.LoginPublicFragmentActivity"
        )
    }

    override val key: String get() = "login_check_box_default"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
