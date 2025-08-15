package com.owo233.tcqt

import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedActionList

object ActionManager {

    private val FIRST_ACTION = GeneratedActionList.ACTIONS // ksp 自动生成

    private val instanceMap = hashMapOf<Class<out IAction>, IAction>()

    private fun instanceOf(cls: Class<out IAction>): IAction =
        instanceMap.getOrPut(cls) {
            try {
                cls.getField("INSTANCE").get(null) as IAction
            } catch (_: NoSuchFieldException) {
                cls.getDeclaredConstructor().newInstance()
            }
        }

    fun runFirst(ctx: Context, proc: ActionProcess) {
        val baseProcs = setOf(
            ActionProcess.MSF,
            ActionProcess.MAIN,
            ActionProcess.TOOL,
            ActionProcess.OPENSDK
        )

        FIRST_ACTION.forEach { actionClass ->
            val action = instanceOf(actionClass)

            val shouldRun =
                ActionProcess.ALL in action.processes ||
                        proc in action.processes ||
                        (ActionProcess.OTHER in action.processes && proc !in baseProcs)

            if (shouldRun) {
                action(ctx, proc)
            }
        }
    }
}
