package com.owo233.tcqt.hooks.func.test

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList

@RegisterAction
@RegisterSetting(
    key = "xm25102rkbec",
    name = "嗨害嗨!就是玩!!!",
    type = SettingType.BOOLEAN,
    desc = "什么也没有,虚无~",
    uiTab = "其他/测试/没路了/还点/不许点了/服了你了/真没路了"
)
class XM25102RKBEC : IAction {

    override val key: String
        get() = GeneratedSettingList.XM25102RKBEC

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
