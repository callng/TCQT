package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.IntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.setObject

@RegisterAction
class ForcedABTest : IAction {

    override val name: String get() = "AB测试强制转组"
    override val desc: String get() = "在AB测试中强制转到指定组，想优先体验某些灰度功能时可以尝试本功能，或者留在对照组。"
    override val uiTab: String get() = "高级"
    override val settings: List<Setting<*>>
        get() = listOf(
            IntSetting(
                "forced_to_ab.mode",
                "强制模式",
                1,
                "",
                listOf("强制A组（对照组）", "强制B组（实验组）")
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) {
        val mode = TCQTSetting.getInt("forced_to_ab.mode")

        val controllerClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ABTestController")
        val expEntityClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")

        expEntityClz.hookMethodBefore(
            "isExpHit",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookMethodBefore("getAssignment") { param ->
            val expName = param.thisObject.invoke("getExpName") as String
            if (!expName.isEmpty()) {
                when (mode) {
                    1 -> param.result = "${expName}_A"
                    2 -> param.result = "${expName}_B"
                }
            }
        }

        expEntityClz.hookMethodBefore(
            "isExperiment",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookMethodBefore(
            "isContrast",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = true
                2 -> param.result = false
            }
        }

        controllerClz.hookMethodAfter(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val entity = param.result
            val mAssignment = entity!!.getObject("mAssignment") as String
            when (mode) {
                1 -> entity.setObject("mAssignment", "${mAssignment}_A")
                2 -> entity.setObject("mAssignment", "${mAssignment}_B")
            }
            param.result = entity
        }
    }

    override val key: String get() = "forced_to_ab"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
