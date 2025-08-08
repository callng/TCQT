package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction

@RegisterAction
class WebJsBridge: AlwaysRunAction() {
    override fun onRun(ctx: Context) {
        TODO("Not yet implemented")
    }

    override val name: String get() = "WebJsBridge"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
