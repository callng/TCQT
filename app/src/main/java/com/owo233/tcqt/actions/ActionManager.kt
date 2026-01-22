package com.owo233.tcqt.actions

import android.content.Context
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.AlwaysRunAction
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedActionList
import com.owo233.tcqt.foundation.utils.log.Log

internal object ActionManager {

    private val FIRST_ACTION = GeneratedActionList.ACTIONS // ksp 自动生成

    private val instanceMap = hashMapOf<Class<out IAction>, IAction>()

    private val failedActions = hashSetOf<Class<out IAction>>()

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
            Log.e("ActionManager instanceOf failed", e)
            failedActions.add(cls)
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
                throw RuntimeException(e)
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

    fun resolve(action: IAction): String {
        if (action.key.isBlank()) {
            // 被R8混淆过的, simpleName也看不懂, 还是直接看报错罢
            return action::class.simpleName ?: "Unknown"
        }

        return GeneratedActionList.ACTION_NAME_MAP[action.key]
            ?: action.key
    }
}
