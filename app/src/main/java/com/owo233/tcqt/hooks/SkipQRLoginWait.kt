package com.owo233.tcqt.hooks

import android.content.Context
import android.os.CountDownTimer
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.PlatformTools
import de.robv.android.xposed.XposedBridge

@RegisterAction
class SkipQRLoginWait: IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (PlatformTools.isMainProcess()) {
            FuzzyClassKit.findClassByMethod(
                prefix = "com.tencent.biz.qrcode.activity.QRLoginAuthActivity",
                isSubClass = true
            ) { clz, _ -> clz.superclass == CountDownTimer::class.java }
                ?.let {
                    XposedBridge.hookAllConstructors(it, beforeHook { param ->
                        param.args[1] = 0
                        param.args[2] = 0
                    })
                }
        }

        // 跳过对话框形式的倒计时等待
        if (PlatformTools.isOpenSdkProcess()) {
            XpClassLoader.load("com.tencent.mobileqq.utils.DialogUtil")
                ?.hookMethod("createCountdownDialog")
                ?.before { param ->
                    if (param.args.size == 10 && param.args[6] is Int) {
                        param.args[6] = 0
                    }
                }
        }
    }

    override val name: String get() = "跳过扫码登录等待"

    override val key: String get() = TCQTSetting.SKIP_QR_LOGIN_WAIT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.OPENSDK)
}
