package com.owo233.tcqt.features.friendhistory

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.host.service.FriendHistoryManager

/**
 * 被删好友通知：由 [FriendHistoryManager] 在好友列表同步时检测到删除后发出。
 *
 * 默认开启（与 QAuxiliary 的 `qn_notify_when_del` 默认值一致）；渠道为模块自建的
 * `tcqt_friend_deleted`（无声 + 震动），与 QQ 自身的消息通知渠道互不影响，也不会写入 QQ 的会话列表。
 *
 * 检测/记录与通知是两个独立开关：只要两个开关中有任意一个开启，就会安装检测 hook；
 * 所以关掉“好友历史记录”只影响历史入口，不会让通知失效。
 *
 * 排除名单里的人不出现在快照里，因此也永远不会产生通知。
 *
 * 注意：key 必须写成字面量（`LegacyGateKeySnapshotTest` 从源码中解析），
 * 改 key 时同步修改 [FriendHistoryManager.NOTIFY_KEY]。
 */
@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.MSF])
object FriendDeletionNotification : Feature(
    key = "friend_deletion_notification",
    name = "被删好友通知",
    desc = "好友列表同步后发现有人把你删除时弹出系统通知。" +
            "是否记录由同分类的「好友历史记录」控制；排除名单里的人不会提醒。",
    uiOrder = 1022,
    defaultEnabled = true,
) {

    override fun install() {
        // 与 FriendHistory 共用同一套检测 hook（install() 幂等），
        // 避免“只开了通知、没开好友历史”时整条链路都不安装。
        FriendHistoryManager.install()
    }
}
