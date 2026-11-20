package com.owo233.tcqt.features.friendhistory

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.host.service.FriendHistoryManager

/**
 * 新增好友通知：好友**真正写入好友变动记录**后发一条系统通知。
 *
 * 与被删好友通知共用同一套检测链路和通知权限检查，但渠道（`tcqt_friend_added`）、
 * 分组与文案完全独立，两者在通知栏可以分别设置。
 *
 * 只有新写入一条「新增好友」记录时才提醒：已在快照里的好友、重复的回包/同步都不会重复通知。
 * 排除名单里的人不会写入快照，因此也永远不会提醒。
 *
 * 注意：key 必须写成字面量（`LegacyGateKeySnapshotTest` 从源码中解析），
 * 改 key 时同步修改 [FriendHistoryManager.NEW_FRIEND_NOTIFY_KEY]。
 */
@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MSF])
object NewFriendNotification : Feature(
    key = "new_friend_notification",
    name = "新增好友通知",
    desc = "好友真正写入好友变动记录后弹出系统通知（显示名 + QQ 号）。" +
            "重复检测与已在记录中的好友不会重复提醒；排除名单里的人不会提醒。",
    uiOrder = 1023,
    defaultEnabled = true,
) {

    override fun install() {
        FriendHistoryManager.install()
    }
}
