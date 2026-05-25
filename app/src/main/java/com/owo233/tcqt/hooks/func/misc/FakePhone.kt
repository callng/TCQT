package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class FakePhone : IAction, DexKitTask {

    override val name: String get() = "伪装手机号码"
    override val desc: String get() = "伪装账号与安全设置页面中的手机号码。"
    override val uiTab: String get() = "杂项"
    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting(
                "fake_phone.string.phone",
                "phone",
                "",
                "",
                "填写要伪装的手机号码，如 1145141919810",
                false
            ),
        )

    private val fakePhone: String by lazy {
        TCQTSetting.getString(
            "fake_phone.string.phone"
        ).ifEmpty { "1145141919810" }
    }

    override val key: String
        get() = "fake_phone"

    override fun onRun(app: Application, process: ActionProcess) {
        requireMethod("fake_phone").hookBefore { param ->
            if (param.args[0] == 5) {
                (param.args[2])?.let { obj ->
                    val bundle = obj as Bundle
                    bundle.putString("phone", fakePhone)
                    param.args[2] = bundle
                }
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "fake_phone" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.app")
            matcher {
                paramTypes = listOf("int", "boolean", "java.lang.Object")
                usingEqStrings("status", "wording", "target_desc", "target_name")
            }
        }
    )
}
