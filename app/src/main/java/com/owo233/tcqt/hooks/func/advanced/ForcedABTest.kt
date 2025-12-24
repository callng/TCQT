package com.owo233.tcqt.hooks.func.advanced

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.getObjectField
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.setObjectField

@RegisterAction
@RegisterSetting(
    key = "forced_to_ab",
    name = "AB测试强制转组",
    type = SettingType.BOOLEAN,
    defaultValue = "false",
    desc = "在AB测试中强制转到指定组，想优先体验某些灰度功能时可以尝试本功能，或者留在对照组。",
    uiTab = "高级"
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
        val mode = GeneratedSettingList.getInt(GeneratedSettingList.FORCED_TO_AB_MODE)

        val controllerClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ABTestController")
        val expEntityClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")

        expEntityClz.hookAfterMethod(
            "isExpHit",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookAfterMethod("getAssignment") { param ->
            val expName = param.thisObject.invoke("getExpName") as String
            if (!expName.isEmpty()) {
                when (mode) {
                    1 -> param.result = "${expName}_A"
                    2 -> param.result = "${expName}_B"
                }
            }
        }

        expEntityClz.hookAfterMethod(
            "isExperiment",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookAfterMethod(
            "isContrast",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = true
                2 -> param.result = false
            }
        }

        controllerClz.hookAfterMethod(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val entity = param.result
            val mAssignment = entity.getObjectField("mAssignment") as String
            when (mode) {
                1 -> entity.setObjectField("mAssignment", "${mAssignment}_A")
                2 -> entity.setObjectField("mAssignment", "${mAssignment}_B")
            }
            param.result = entity
        }
    }

    override val key: String get() = GeneratedSettingList.FORCED_TO_AB

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}