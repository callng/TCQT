package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess

@RegisterAction(processes = [ActionProcess.ALL])
object DisableHotPatch : Feature(
    key = "disable_hot_patch",
    name = "禁用热补丁加载",
    desc = "顾名思义，但不会删除已有的热补丁文件。",
) {


    override fun install() = Unit
}
