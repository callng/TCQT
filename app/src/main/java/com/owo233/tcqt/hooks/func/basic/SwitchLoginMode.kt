package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.IntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.paramCount
import com.tencent.common.config.pad.DeviceType

@RegisterAction
class SwitchLoginMode : IAction {

    override val name: String get() = "切换登录模式"
    override val desc: String get() = "在不改变UI的情况下以手机或平板模式登录账号，一个账号可以两处登录互不干扰。"
    override val uiTab: String get() = "基础"
    override val uiOrder: Int get() = 2
    override val settings: List<Setting<*>>
        get() = listOf(
            IntSetting("switch_login_mode.type", "登录类型", 1, "", listOf("手机模式", "平板模式")),
        )

    override fun onRun(app: Application, process: ActionProcess) {
        val loginType = TCQTSetting.getInt("switch_login_mode.type")

        loadOrThrow("com.tencent.common.config.pad.PadUtil")
            .declaredMethods.first {
                it.returnType == DeviceType::class.java && it.paramCount == 1
                        && it.parameterTypes[0] == Context::class.java
            }.hookBefore { param ->
                when (loginType) {
                    1 -> param.result = DeviceType.PHONE
                    2 -> param.result = DeviceType.TABLET
                }
            }
    }

    override val key: String get() = "switch_login_mode"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)
}
