package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hookAfterMethod

@RegisterAction
@RegisterSetting(
    key = "forced_to_ab",
    name = "AB测试强制转组",
    type = SettingType.BOOLEAN,
    defaultValue = "false",
    desc = "在AB测试中强制转到指定组，想优先体验某些灰度功能时可以尝试本功能，或者留在对照组。",
    uiTab = "高级",
    uiOrder = 106
)
@RegisterSetting(
    key = "forced_to_ab.mode",
    name = "强制模式",
    type = SettingType.INT,
    defaultValue = "1",
    options = "强制A组（对照组）|强制B组（实验组）",
)
class ForcedABTest : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val controllerClz = load("com.tencent.mobileqq.utils.abtest.ABTestController")!!
        val expEntityClz = load("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")!!

        val onlineField = expEntityClz.getDeclaredField("isExpOnline")
            .apply { isAccessible = true }
        val assignmentField = expEntityClz.getDeclaredField("mAssignment")
            .apply { isAccessible = true }
        val expGrayIdField = expEntityClz.getDeclaredField("mExpGrayId")
            .apply { isAccessible = true }
        val layerNameField = expEntityClz.getDeclaredField("mLayerName")
            .apply { isAccessible = true }

        controllerClz.getDeclaredMethod(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ).hookAfterMethod { param ->
            val result = param.result

            val isOnline = onlineField.getBoolean(result)
            val assignment = assignmentField.get(result) as String
            val keyName = param.args[1] as String

            /*if (assignment.endsWith("_A") || assignment.endsWith("_B")) {
                Log.d("result: ${result.toJsonString()}")
            }*/

            if (!isOnline) return@hookAfterMethod

            val mode = GeneratedSettingList.getInt(GeneratedSettingList.FORCED_TO_AB_MODE)
            when (mode) {
                1 -> {
                    // 强制A组（对照组） - 选项索引0
                    if (!assignment.endsWith("_A")) {
                        assignmentField.set(result, "${keyName}_A")
                        expGrayIdField.set(result, "114514")
                        layerNameField.set(result, keyName)
                        param.result = result
                    }
                }
                2 -> {
                    // 强制B组（实验组） - 选项索引1
                    if (!assignment.endsWith("_B")) {
                        assignmentField.set(result, "${keyName}_B")
                        expGrayIdField.set(result, "114514")
                        layerNameField.set(result, keyName)
                        param.result = result
                    }
                }
            }
        }
    }

    override val key: String get() = GeneratedSettingList.FORCED_TO_AB

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
