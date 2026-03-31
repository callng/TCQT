package com.owo233.tcqt.hooks.func.misc

import android.content.Context
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.owo233.tcqt.utils.hook.isNotStatic
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
@RegisterSetting(
    key = "disable_qq_crash_report_manager",
    name = "禁用QQ崩溃报告管理器",
    type = SettingType.BOOLEAN,
    desc = "没有实际意义的功能，仅供测试使用。",
    uiTab = "杂项"
)
class DisableQQCrashReportManager : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val doNothing: (HookParam) -> Unit = { it.result = Unit }

        val allArgs = if (HookEnv.isQQ()) {
            arrayOf(Boolean::class.java, String::class.java)
        } else {
            arrayOf(Boolean::class.java)
        }

        load("com.tencent.qqperf.monitor.crash.QQCrashReportManager")?.let {
            it.declaredMethods.first { method ->
                method.isNotStatic && method.returnType == Void.TYPE && method.paramCount == 2
            }.hookAfter { param ->
                param.result = Unit
            }
        }

        load("com.tencent.qqperf.monitor.crash.QQCrashHandleListener")?.let {
            it.hookMethodAfter("onCrashHandleStart", *allArgs, block = doNothing)

            it.hookMethodAfter("onCrashHandleEnd", Boolean::class.java, block = doNothing)

            it.hookMethodAfter(
                "onCrashSaving",
                Boolean::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, String::class.java, String::class.java,
                String::class.java, String::class.java,
                block = doNothing
            )
        }

        load("com.tencent.mobileqq.msf.MSFCrashHandleListener")?.let {
            it.hookMethodAfter("onCrashHandleStart", *allArgs, block = doNothing)

            it.hookMethodAfter("onCrashHandleEnd", Boolean::class.java, block = doNothing)

            it.hookMethodAfter(
                "onCrashSaving",
                Boolean::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, String::class.java, String::class.java,
                String::class.java, String::class.java,
                block = doNothing
            )
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_QQ_CRASH_REPORT_MANAGER
}
