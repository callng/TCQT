package com.owo233.tcqt.features.checkin

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess

/**
 * 群打卡单条通知模式。
 *
 * 只决定通知的**形式**，与是否发通知无关：
 * - 开启（默认）：整轮只有一条通知。开始时就地出现，执行中逐群刷新正文
 *   （形如「已完成 12/56，跳过 3，失败 0」），结束时把同一条改成结果文案；
 *   开始新一轮时会先撤掉上一轮的结果，通知栏里任何时候最多只剩这一条。
 * - 关闭：恢复旧行为——开始一条、结束另发一条结果，结果通知会逐轮累积。
 *
 * 是否真的发通知由 [GroupCheckInNotification] 控制，两者独立：关掉那个开关时，
 * 本开关无论开关都不会产生任何通知。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object GroupCheckInSingleNotification : Feature(
    key = "group_check_in_single_notification",
    name = "单条通知模式",
    desc = "开启时整轮只保留一条通知：开始时出现，执行中逐群刷新进度，结束时原地改成结果。" +
            "关闭则恢复「开始一条 + 结束另发一条」，历史结果会累积。" +
            "只决定通知形式，是否发通知由「群打卡通知」控制。",
    uiOrder = 13,
    defaultEnabled = true,
) {

    override fun install() = Unit
}
