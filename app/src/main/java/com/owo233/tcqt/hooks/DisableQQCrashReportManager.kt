package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isStatic

@RegisterAction
@RegisterSetting(
    key = "disable_qq_crash_report_manager",
    name = "禁用QQ崩溃报告管理器",
    type = SettingType.BOOLEAN,
    desc = "没有实际意义的功能，仅供测试使用。",
    uiTab = "高级"
)
class DisableQQCrashReportManager : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        load("com.tencent.qqperf.monitor.crash.QQCrashReportManager")?.let {
            it.declaredMethods.first { method ->
                !method.isStatic && method.returnType == Void.TYPE && method.parameterTypes.size == 2
            }.hookBeforeMethod { param ->
                param.result = Unit
            }
        }

        load("com.tencent.qqperf.monitor.crash.QQCrashHandleListener")?.let {
            val hooker = beforeHook { param -> param.result = Unit }
            it.hookMethod(
                "onCrashHandleStart",
                Boolean::class.java,
                String::class.java,
                hooker
            )
            it.hookMethod("onCrashHandleEnd", Boolean::class.java, hooker)
            it.hookMethod(
                "onCrashSaving",
                Boolean::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                hooker
            )
        }

        load("com.tencent.mobileqq.msf.MSFCrashHandleListener")?.let {
            val hooker = beforeHook { param -> param.result = Unit }
            it.hookMethod(
                "onCrashHandleStart",
                Boolean::class.java,
                String::class.java,
                hooker
            )
            it.hookMethod("onCrashHandleEnd", Boolean::class.java, hooker)
            it.hookMethod(
                "onCrashSaving",
                Boolean::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                hooker
            )
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_QQ_CRASH_REPORT_MANAGER
}
