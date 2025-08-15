package com.owo233.tcqt.hooks

import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.internals.setting.TCQTSetting
import de.robv.android.xposed.XposedBridge

@RegisterAction
class LoginCheckBoxDefault: IAction {

    companion object {
        private val loginContextNames = setOf(
            "com.tencent.mobileqq.activity.LoginActivity",
            "com.tencent.mobileqq.activity.LoginPublicFragmentActivity"
        )
    }

    override fun onRun(ctx: Context, process: ActionProcess) {
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

    override val key: String get() = TCQTSetting.LOGIN_CHECK_BOX_DEFAULT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
