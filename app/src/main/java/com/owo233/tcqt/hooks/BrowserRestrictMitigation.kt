package com.owo233.tcqt.hooks

import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.FuzzyClassKit
import com.owo233.tcqt.utils.hookBeforeMethod


@RegisterAction
class BrowserRestrictMitigation : AlwaysRunAction() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        FuzzyClassKit.findMethodByClassPrefix(
            prefix = "com.tencent.mobileqq.webview.WebSecurityPluginV2",
            isSubClass = true
        ) { _, method ->
            method.parameterCount == 1 && method.parameterTypes[0] == Bundle::class.java
        }?.hookBeforeMethod {
            val bundle = it.args[0] as Bundle
            if (bundle.getInt("jumpResult", 0) != 0) {
                bundle.putInt("jumpResult", 0)
                bundle.putString("jumpUrl", "")
            }
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
