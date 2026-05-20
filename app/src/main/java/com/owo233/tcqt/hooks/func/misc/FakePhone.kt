package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
@RegisterSetting(
    key = "fake_phone",
    name = "伪装手机号码",
    type = SettingType.BOOLEAN,
    desc = "伪装账号与安全设置页面中的手机号码。",
    uiTab = "杂项"
)
@RegisterSetting(
    key = "fake_phone.string.phone",
    name = "phone",
    type = SettingType.STRING,
    textAreaPlaceholder = "填写要伪装的手机号码，如 1145141919810"
)
class FakePhone : IAction, DexKitTask {

    private val fakePhone: String by lazy {
        GeneratedSettingList.getString(
            GeneratedSettingList.FAKE_PHONE_STRING_PHONE
        ).ifEmpty { "1145141919810" }
    }

    override val key: String
        get() = GeneratedSettingList.FAKE_PHONE

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
