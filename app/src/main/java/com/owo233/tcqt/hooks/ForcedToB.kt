package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting

@RegisterAction
class ForcedToB: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        val controllerClz = XpClassLoader.load("com.tencent.mobileqq.utils.abtest.ABTestController")!!
        val expEntityClz = XpClassLoader.load("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")!!

        controllerClz.getDeclaredMethod(
            "getExpEntity",
            String::class.java,
            String::class.java
        ).hookMethod(afterHook { param ->
            val key = param.args[1] as String
            val origin = param.result

            val assignmentField = expEntityClz.getDeclaredField("mAssignment")
                .apply { isAccessible = true }
            assignmentField.set(origin, "${key}_B")

            val expGrayIdField = expEntityClz.getDeclaredField("mExpGrayId")
                .apply { isAccessible = true }
            expGrayIdField.set(origin, "114514")

            val layerNameField = expEntityClz.getDeclaredField("mLayerName")
                .apply { isAccessible = true }
            layerNameField.set(origin, key)

            param.result = origin
        })
    }

    override val name: String get() = "AB测试，强制跳转到实验组"

    override val key: String get() = TCQTSetting.FORCED_TO_B

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
