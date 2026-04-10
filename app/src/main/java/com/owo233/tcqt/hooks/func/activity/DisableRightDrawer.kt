package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.Visibility
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
@RegisterSetting(
    key = "disable_right_drawer",
    name = "禁用聊天右侧抽屉",
    type = SettingType.BOOLEAN,
    desc = "屏蔽在聊天界面向左滑动呼出右侧面板（如群应用、亲密关系等）",
    uiTab = "界面"
)
class DisableRightDrawer : IAction {

    override val key: String
        get() = GeneratedSettingList.DISABLE_RIGHT_DRAWER

    override fun onRun(app: Application, process: ActionProcess) {
        loadOrThrow("com.tencent.aio.frame.drawer.DrawerFrameViewGroup").findMethod {
            visibility = Visibility.PRIVATE
            paramCount = 2
            paramTypes(float, string)
        }.hookBefore { param ->
            val dx = param.args[0] as? Float ?: return@hookBefore
            if (dx < 0.0f) {
                param.result = false
            }
        }
    }
}
