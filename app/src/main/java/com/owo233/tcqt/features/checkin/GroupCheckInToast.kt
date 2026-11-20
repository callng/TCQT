package com.owo233.tcqt.features.checkin

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess

/**
 * 群打卡完成提示（QQ 内置轻提示）。
 *
 * 与 [GroupCheckInNotification] 是两个独立开关，语义对齐 XAutoDaily 的
 * 「签到提示」与「启用签到通知」：可单开、可同开、互不覆盖。
 * 关闭后只影响提示，不影响打卡执行与完成记录。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object GroupCheckInToast : Feature(
    key = "group_check_in_toast",
    name = "群打卡提示",
    desc = "打卡完成后在 QQ 界面内弹出轻提示；QQ 在后台运行时可能看不到。与「群打卡通知」相互独立。",
    uiOrder = 11,
    defaultEnabled = true,
) {

    override fun install() = Unit
}
