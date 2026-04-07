package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.content.Context
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.reflect.allConstructors
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
@RegisterSetting(
    key = "skip_qr_login_wait",
    name = "跳过扫码登录等待",
    type = SettingType.BOOLEAN,
    desc = "扫码登录时跳过倒计时。",
)
class SkipQRLoginWait : IAction, DexKitTask {

    override fun onRun(app: Application, process: ActionProcess) {
        if (process == ActionProcess.MAIN) {
            requireClass("skip_qr_login_wait").allConstructors().forEach {
                it.hookBefore { param ->
                    param.args[1] = 0L
                    param.args[2] = 0L
                }
            }
        }

        // 跳过对话框形式的倒计时等待
        if (process == ActionProcess.OPENSDK) {
            loadOrThrow("com.tencent.mobileqq.utils.DialogUtil").hookMethodBefore(
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

    override val processes: Set<ActionProcess>
        get() = setOf(
            ActionProcess.MAIN,
            ActionProcess.OPENSDK
        )

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "skip_qr_login_wait" to FindClass().apply {
            searchPackages("com.tencent.biz.qrcode.activity")
            matcher {
                superClass("android.os.CountDownTimer")
                addFieldForType("com.tencent.biz.qrcode.activity.QRLoginAuthActivity")
                methods {
                    add { name("onFinish") }
                    add { name("onTick") }
                }
            }
        }
    )
}
