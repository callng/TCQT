package com.owo233.tcqt.hooks.func.test

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction

@RegisterAction
class XM25102RKBEC : IAction {

    override val name: String get() = "木兰花·拟古决绝词柬友"
    override val desc: String get() = """
        人生若只如初见，何事秋风悲画扇。
        等闲变却故人心，却道故人心易变。
        骊山语罢清宵半，泪雨零铃终不怨。
        何如薄幸锦衣郎，比翼连枝当日愿。
    """.trimIndent()
    override val uiTab: String get() = "其他"

    override val key: String
        get() = "xm25102rkbec_2"

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
