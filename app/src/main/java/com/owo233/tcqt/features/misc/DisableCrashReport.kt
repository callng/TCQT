package com.owo233.tcqt.features.misc

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.doNothing
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction(processes = [ActionProcess.ALL])
object DisableCrashReport : Feature(
    key = "disable_qq_crash_report_manager",
    name = "禁用崩溃上报",
    desc = "禁止BuglySDK初始化，用途意义不明。",
) {

    override fun install() {
        "com.tencent.feedback.eup.CrashReport".toClass.findMethod {
            name = "initCrashReport"
            isStatic = true
            paramTypes = arrayOf(context, string, boolean, null, long)
        }.doNothing()
    }
}
