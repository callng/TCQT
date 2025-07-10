package com.owo233.tcqt.hooks

import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import de.robv.android.xposed.XposedBridge

class LoginCheckBoxDefault: IAction {

    companion object {
        private val loginContextNames = setOf(
            "com.tencent.mobileqq.activity.LoginActivity",
            "com.tencent.mobileqq.activity.LoginPublicFragmentActivity"
        )
    }

    override fun onRun(ctx: Context) {
        XposedBridge.hookAllConstructors(CheckBox::class.java, afterHook {
            val context = it.args.getOrNull(0) as? Context ?: return@afterHook
            val className = context.javaClass.name

            if (!loginContextNames.contains(className)) return@afterHook

            val checkBox = it.thisObject as CheckBox

            if (!checkBox.isChecked) {
                checkBox.post { checkBox.isChecked = true }
            }
        })
    }

    override val name: String get() = "默认勾选复选框协议"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
