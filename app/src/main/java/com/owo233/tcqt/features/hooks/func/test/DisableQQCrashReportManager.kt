package com.owo233.tcqt.features.hooks.func.test

import android.content.Context
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.load
import com.owo233.tcqt.foundation.utils.beforeHook
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.owo233.tcqt.foundation.utils.hookMethod
import com.owo233.tcqt.foundation.utils.isNotStatic
import com.owo233.tcqt.foundation.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "disable_qq_crash_report_manager",
    name = "禁用QQ崩溃报告管理器",
    type = SettingType.BOOLEAN,
    desc = "没有实际意义的功能，仅供测试使用。",
    uiTab = "调试"
)
class DisableQQCrashReportManager : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val hooker = beforeHook { param -> param.result = Unit }

        val allArgs = if (HookEnv.isQQ()) {
            arrayOf(Boolean::class.java, String::class.java, hooker)
        } else {
            arrayOf(Boolean::class.java, hooker)
        }

        load("com.tencent.qqperf.monitor.crash.QQCrashReportManager")?.let {
            it.declaredMethods.first { method ->
                method.isNotStatic && method.returnType == Void.TYPE && method.paramCount == 2
            }.hookBeforeMethod { param ->
                param.result = Unit
            }
        }

        load("com.tencent.qqperf.monitor.crash.QQCrashHandleListener")?.let {
            it.hookMethod("onCrashHandleStart", *allArgs)

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
            it.hookMethod("onCrashHandleStart", *allArgs)

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
