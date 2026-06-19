package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.doNothing
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class DisableCrashReport : IAction {

    override val key: String get() = "disable_qq_crash_report_manager"
    override val name: String get() = "禁用崩溃上报"
    override val desc: String get() = "禁止BuglySDK初始化，用途意义不明。"
    override val uiTab: String get() = "杂项"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.feedback.eup.CrashReport".toClass.findMethod {
            name = "initCrashReport"
            isStatic = true
            paramTypes = arrayOf(context, string, boolean, null, long)
        }.doNothing()
    }
}
