package com.owo233.tcqt

import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.BrowserRestrictMitigation
import com.owo233.tcqt.hooks.FetchService

object ActionManager {

    private val FIRST_ACTION = arrayOf(
        FetchService::class.java,
        BrowserRestrictMitigation::class.java
    )

    private val instanceMap = hashMapOf<Class<*>, IAction>()

    private fun instanceOf(cls: Class<*>): IAction = instanceMap.getOrPut(cls) {
        cls.getConstructor().newInstance() as IAction
    }

    fun runFirst(ctx: Context, proc: ActionProcess) {
        FIRST_ACTION.forEach {
            val action = instanceOf(it)
            if (proc == action.process) { //  || proc == ActionProcess.ALL
                action(ctx)
            }
        }
    }
}
