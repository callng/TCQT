package com.owo233.tcqt

import android.content.Context
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedActionList
import com.owo233.tcqt.utils.Log

object ActionManager {

    private val FIRST_ACTION = GeneratedActionList.ACTIONS // ksp 自动生成

    private val instanceMap = hashMapOf<Class<out IAction>, IAction>()

    private val failedActions = hashSetOf<Class<out IAction>>()

    private fun getActionName(cls: Class<out IAction>): String {
        return runCatching {
            val annotations = cls.getAnnotationsByType(RegisterSetting::class.java)
            annotations.firstOrNull()?.name ?: cls.simpleName
        }.getOrDefault(cls.simpleName)
    }

    private fun instanceOf(cls: Class<out IAction>): IAction? {
        if (cls in failedActions) return null

        return runCatching {
            instanceMap.getOrPut(cls) {
                try {
                    cls.getField("INSTANCE").get(null) as IAction
                } catch (_: NoSuchFieldException) {
                    cls.getDeclaredConstructor().newInstance()
                }
            }
        }.getOrElse { e ->
            failedActions.add(cls)
            Log.e("Action [${getActionName(cls)}] 实例化失败", e)
            null
        }
    }

    fun runFirst(ctx: Context, proc: ActionProcess) {
        val baseProcs = setOf(
            ActionProcess.MSF,
            ActionProcess.MAIN,
            ActionProcess.TOOL,
            ActionProcess.OPENSDK,
            ActionProcess.QZONE
        )

        FIRST_ACTION.forEach { actionClass ->
            runCatching {
                val action = instanceOf(actionClass) ?: return@forEach

                val shouldRun =
                    ActionProcess.ALL in action.processes ||
                            proc in action.processes ||
                            (ActionProcess.OTHER in action.processes && proc !in baseProcs)

                if (shouldRun) {
                    action(ctx, proc)
                }
            }.onFailure { e ->
                Log.e("Action [${getActionName(actionClass)}] 执行过程异常", e)
            }
        }
    }

    fun getEnabledActionCount(): Int {
        return FIRST_ACTION.count {
            runCatching {
                val action = instanceOf(it) ?: return@count false
                action !is AlwaysRunAction && action.canRun()
            }.getOrDefault(false)
        }
    }

    fun getDisabledActionCount(): Int {
        return FIRST_ACTION.count {
            runCatching {
                val action = instanceOf(it) ?: return@count false
                action !is AlwaysRunAction && !action.canRun()
            }.getOrDefault(false)
        }
    }
}
