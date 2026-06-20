package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.reflect.setValue

@RegisterAction
class OpenQLog : IAction {

    override val key: String get() = "open_q_log"
    override val name: String get() = "日志输出到Logcat"
    override val desc: String get() = "没事别瞎打开(可能会影响性能)，只是为了方便调试。"
    override val uiTab: String get() = "杂项"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.qphone.base.util.QLog".toClass.apply {
            setValue("useXlog", false)
            setValue("UIN_REPORTLOG_LEVEL", 4)
        }
    }
}
