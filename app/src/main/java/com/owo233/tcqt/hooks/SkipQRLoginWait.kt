package com.owo233.tcqt.hooks

import android.content.Context
import android.os.CountDownTimer
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.beforeHook
import de.robv.android.xposed.XposedBridge

class SkipQRLoginWait: IAction {

    override fun onRun(ctx: Context) {
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

    override val name: String get() = "跳过扫码登录等待"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
