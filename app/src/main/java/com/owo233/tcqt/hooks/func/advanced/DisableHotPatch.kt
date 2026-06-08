package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction

@RegisterAction
class DisableHotPatch : IAction {

    override val key: String get() = "disable_hot_patch"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
    override val name: String get() = "禁用热补丁加载"
    override val desc: String get() = "顾名思义，但不会删除已有的热补丁文件。"
    override val uiTab: String get() = "高级"

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
