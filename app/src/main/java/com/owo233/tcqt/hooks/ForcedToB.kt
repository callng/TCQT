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
@RegisterSetting(
    key = "forced_to_b",
    name = "AB测试强制转B组",
    type = SettingType.BOOLEAN,
    desc = "启用后，在AB测试对照组中，将全部测试强制转为B组（实验组），注意，此功能有一定的风险，请自行斟酌是否启用。",
    isRedMark = true,
    uiOrder = 13
)
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
