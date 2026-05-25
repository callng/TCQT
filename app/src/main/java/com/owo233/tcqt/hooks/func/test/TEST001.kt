package com.owo233.tcqt.hooks.func.test

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction

@RegisterAction
class TEST001 : IAction {

    override val name: String get() = "不负如来不负卿"
    override val desc: String get() = """
        曾虑多情损梵行，入山又恐别倾城。
        世间安得双全法，不负如来不负卿。
    """.trimIndent()
    override val uiTab: String get() = "其他/测试"

    override val key: String get() = "TEST001"

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
