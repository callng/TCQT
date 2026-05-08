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
    key = "xm25102rkbec_0",
    name = "木兰花·拟古决绝词柬友",
    type = SettingType.BOOLEAN,
    desc = """
        人生若只如初见，何事秋风悲画扇。
        等闲变却故人心，却道故人心易变。
        骊山语罢清宵半，泪雨零铃终不怨。
        何如薄幸锦衣郎，比翼连枝当日愿。
    """,
    uiTab = "其他"
)
@RegisterSetting(
    key = "xm25102rkbec_1",
    name = "不负如来不负卿",
    type = SettingType.BOOLEAN,
    desc = """
        曾虑多情损梵行，入山又恐别倾城。
        世间安得双全法，不负如来不负卿。
    """,
    uiTab = "其他/测试"
)
@RegisterSetting(
    key = "xm25102rkbec_2",
    name = "十诫诗",
    type = SettingType.BOOLEAN,
    desc = """
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
    """,
    uiTab = "其他/测试/没路了/还点/不许点了/服了你了/真没路了"
)
class XM25102RKBEC : IAction {

    override val key: String
        get() = GeneratedSettingList.XM25102RKBEC_2

    override fun onRun(app: Application, process: ActionProcess) = Unit
}
