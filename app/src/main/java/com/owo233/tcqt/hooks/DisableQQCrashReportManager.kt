package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.isStatic

@RegisterAction
@RegisterSetting(
    key = "disable_qq_crash_report_manager",
    name = "禁用QQ崩溃报告管理器",
    type = SettingType.BOOLEAN,
    desc = "没有实际意义的功能，仅供测试使用。",
    isRedMark = true,
    uiOrder = 7
)
class DisableQQCrashReportManager: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.qqperf.monitor.crash.QQCrashReportManager")?.let {
            it.declaredMethods.first { method ->
                !method.isStatic && method.returnType == Void.TYPE && method.parameterTypes.size == 2
            }.hookMethod(beforeHook { param ->
                param.result = Unit
            })
        }

        XpClassLoader.load("com.tencent.qqperf.monitor.crash.QQCrashHandleListener")?.let {
            val hooker = beforeHook { param -> param.result = Unit }
            it.hookMethod("onCrashHandleStart", hooker)
            it.hookMethod("onCrashHandleEnd", hooker)
            it.hookMethod("onCrashSaving", hooker)
        }

        XpClassLoader.load("com.tencent.mobileqq.msf.MSFCrashHandleListener")?.let {
            val hooker = beforeHook { param -> param.result = Unit }
            it.hookMethod("onCrashHandleStart", hooker)
            it.hookMethod("onCrashHandleEnd", hooker)
            it.hookMethod("onCrashSaving", hooker)
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_QQ_CRASH_REPORT_MANAGER
}
