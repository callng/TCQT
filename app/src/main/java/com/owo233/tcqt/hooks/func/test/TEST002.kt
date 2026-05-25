package com.owo233.tcqt.hooks.func.test

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction

@RegisterAction
class TEST002 : IAction {

    override val name: String get() = "十诫诗"
    override val desc: String get() = """
        第一最好不相见，如此便可不相恋。
        第二最好不相知，如此便可不相思。
        第三最好不相伴，如此便可不相欠。
        第四最好不相惜，如此便可不相忆。
        第五最好不相爱，如此便可不相弃。
        第六最好不相对，如此便可不相会。
        第七最好不相误，如此便可不相负。
        第八最好不相许，如此便可不相续。
        第九最好不相依，如此便可不相偎。
        第十最好不相遇，如此便可不相聚。
        但曾相见便相知，相见何如不见时。
        安得与君相诀绝，免教生死作相思。
    """.trimIndent()
    override val uiTab: String get() = "其他/测试/多级目录"

    override val key: String get() = "TEST002"

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
