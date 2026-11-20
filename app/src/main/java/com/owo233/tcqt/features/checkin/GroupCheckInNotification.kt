package com.owo233.tcqt.features.checkin

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess

/**
 * 群打卡通知（系统通知）。
 *
 * 与 [GroupCheckInToast] 是两个独立开关，语义对齐 XAutoDaily 的「启用签到通知」：
 * 执行中挂常驻通知，结束后替换为结果通知，QQ 在后台/无界面时也能看到。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object GroupCheckInNotification : Feature(
    key = "group_check_in_notification",
    name = "群打卡通知",
    desc = "打卡开始与完成时发送系统通知，QQ 在后台运行时也能看到。与「群打卡提示」相互独立。",
    uiOrder = 12,
    // 默认开启：这是唯一能在 QQ 后台/无界面时看到打卡结果的通道。
    // 旧版本默认关闭，导致“开始一条 + 结束一条”实际一条都收不到。
    defaultEnabled = true,
) {

    override fun install() = Unit
}
