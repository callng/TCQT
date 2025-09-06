package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList

@RegisterAction
@RegisterSetting(key = "forced_to_b", name = "AB测试，强制跳转到实验组", type = SettingType.BOOLEAN)
class ForcedToB: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        val controllerClz = XpClassLoader.load("com.tencent.mobileqq.utils.abtest.ABTestController")!!
        val expEntityClz = XpClassLoader.load("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")!!

        val onlineField = expEntityClz.getDeclaredField("isExpOnline")
            .apply { isAccessible = true }
        val assignmentField = expEntityClz.getDeclaredField("mAssignment")
            .apply { isAccessible = true }
        val expGrayIdField = expEntityClz.getDeclaredField("mExpGrayId")
            .apply { isAccessible = true }
        val layerNameField = expEntityClz.getDeclaredField("mLayerName")
            .apply { isAccessible = true }

        controllerClz.getDeclaredMethod(
            "getExpEntity",
            String::class.java,
            String::class.java
        ).hookMethod(afterHook { param ->
            val result = param.result ?: return@afterHook

            val isOnline = onlineField.getBoolean(result)
            val assignment = assignmentField.get(result) as String

            if (isOnline && !assignment.endsWith("_B")) {
                val keyName = param.args[1] as String

                assignmentField.set(result, "${keyName}_B")
                expGrayIdField.set(result, "114514")
                layerNameField.set(result, keyName)

                param.result = result
            }
        })
    }

    override val key: String get() = GeneratedSettingList.FORCED_TO_B

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
