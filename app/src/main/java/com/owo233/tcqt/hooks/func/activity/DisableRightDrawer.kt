package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.Visibility
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class DisableRightDrawer : IAction {

    override val name: String get() = "禁用聊天右侧抽屉"
    override val desc: String get() = "屏蔽在聊天界面向左滑动呼出右侧面板（如群应用、亲密关系等）"
    override val uiTab: String get() = "界面"
    override val key: String
        get() = "disable_right_drawer"

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
