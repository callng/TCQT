package com.owo233.tcqt.hooks

import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.generated.GeneratedSettingList
import de.robv.android.xposed.XposedBridge

@RegisterAction
@RegisterSetting(
    key = "login_check_box_default",
    name = "默认勾选登录协议",
    type = SettingType.BOOLEAN,
    desc = "登录界面自动勾选复选框（用户协议，有人看了吗）。",
    uiOrder = 15
)
class LoginCheckBoxDefault : IAction {

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

    override val key: String get() = GeneratedSettingList.LOGIN_CHECK_BOX_DEFAULT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
