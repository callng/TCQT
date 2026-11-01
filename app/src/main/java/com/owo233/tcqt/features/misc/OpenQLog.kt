package com.owo233.tcqt.features.misc

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.reflect.setValue

@RegisterAction(processes = [ActionProcess.ALL])
object OpenQLog : Feature(
    key = "open_q_log",
    name = "日志输出到Logcat",
    desc = "没事别瞎打开(可能会影响性能)，只是为了方便调试。",
) {

    override fun install() {
        "com.tencent.qphone.base.util.QLog".toClass.apply {
            setValue("useXlog", false)
            setValue("UIN_REPORTLOG_LEVEL", 4)
        }
    }
}
