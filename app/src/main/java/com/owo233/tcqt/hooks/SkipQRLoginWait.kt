package com.owo233.tcqt.hooks

import android.content.Context
import android.os.CountDownTimer
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.FuzzyClassKit
import com.owo233.tcqt.utils.hookBeforeAllConstructors
import com.owo233.tcqt.utils.hookBeforeMethod

@RegisterAction
@RegisterSetting(
    key = "skip_qr_login_wait",
    name = "跳过扫码登录等待",
    type = SettingType.BOOLEAN,
    desc = "扫码登录时跳过倒计时。",
    uiOrder = 24
)
class SkipQRLoginWait : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (process == ActionProcess.MAIN) {
            val targetClass = FuzzyClassKit.findClassByMethod(
                prefix = "com.tencent.biz.qrcode.activity.QRLoginAuthActivity",
                isSubClass = true
            ) { clz, _ ->
                clz.superclass == CountDownTimer::class.java
            } ?: FuzzyClassKit.findClassByMethod(
                prefix = "com.tencent.biz.qrcode.activity",
                isSubClass = false
            ) { clz, _ ->
                clz.superclass == CountDownTimer::class.java
            } ?: error("跳过登录等待失败,找不到符合要求的类 -> superclass == CountDownTimer::class.java")

            targetClass.hookBeforeAllConstructors { param ->
                param.args[1] = 0L
                param.args[2] = 0L
            }
        }

        // 跳过对话框形式的倒计时等待
        if (process == ActionProcess.OPENSDK) {
            load("com.tencent.mobileqq.utils.DialogUtil")!!
                .hookBeforeMethod(
                    "createCountdownDialog",
                    Context::class.java,
                    String::class.java,
                    CharSequence::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    View.OnClickListener::class.java,
                    View.OnClickListener::class.java
                ) { param ->
                    if (param.args.size == 10 && param.args[6] is Int) {
                        param.args[6] = 0
                    }
                }
        }
    }

    override val key: String get() = GeneratedSettingList.SKIP_QR_LOGIN_WAIT

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.OPENSDK)
}
