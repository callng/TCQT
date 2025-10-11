package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount
import com.tencent.qphone.base.remote.FromServiceMsg

@RegisterAction
@RegisterSetting(
    key = "disable_dialog",
    name = "屏蔽烦人弹窗",
    type = SettingType.BOOLEAN,
    defaultValue = "true",
    desc = "将一些烦人的弹窗给屏蔽掉，现支持「灰度版本体验」及「社交封禁提醒」弹窗。",
    uiOrder = 4
)
class DisableDialog : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        disableGrayCheckDialog()
        disableFekitDialog()
    }

    private fun disableGrayCheckDialog() {
        val grayCheckClass =
            XpClassLoader.load("com.tencent.mobileqq.graycheck.business.GrayCheckHandler")
                ?: error("无法加载GrayCheckHandler类,屏蔽GrayCheckDialog不会生效!")

        val hookMethod = grayCheckClass.declaredMethods.firstOrNull {
            it.isPublic && it.returnType == Void.TYPE &&
                    it.paramCount == 1 && it.parameterTypes[0] == FromServiceMsg::class.java
        } ?: error("无法找到GrayCheckHandler中被混淆的方法,屏蔽GrayCheckDialog不会生效!")

        hookMethod.hookBeforeMethod { it.result = Unit }
    }

    private fun disableFekitDialog() {
        val dtapClass = XpClassLoader.load("com.tencent.mobileqq.dt.api.impl.DTAPIImpl")
            ?: error("无法加载DTAPIImpl类,屏蔽FekitDialog不会生效!")
        dtapClass.hookMethod(
            "onSecDispatchToAppEvent",
            String::class.java,
            ByteArray::class.java,
            beforeHook { param ->
                val str = param.args[0] as String
                if (str == "socialError") {
                    param.result = Unit
                }
            }
        )
    }

    override val key: String get() = GeneratedSettingList.DISABLE_DIALOG
}
