package com.owo233.tcqt.features.misc

import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.annotations.ActionPriority
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.allConstructors

@RegisterAction(processes = [ActionProcess.MAIN], priority = ActionPriority.CRITICAL)
object LoginCheckBoxDefault : Feature(
    key = "login_check_box_default",
    name = "默认勾选登录协议",
    desc = "登录界面自动勾选复选框（用户协议，有人看了吗）。",
) {

    override fun install() {
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

    private val loginContextNames = setOf(
        "com.tencent.mobileqq.activity.LoginActivity",
        "com.tencent.mobileqq.activity.LoginPublicFragmentActivity"
    )
}
