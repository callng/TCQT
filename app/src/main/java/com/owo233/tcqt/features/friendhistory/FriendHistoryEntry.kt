package com.owo233.tcqt.features.friendhistory

import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.FriendHistoryStore

/**
 * 历史好友列表页的独立入口。
 *
 * 入口与记录开关分离：不随 `friend_history` 开关显隐，关闭记录后仍可查看已有数据。
 * `ActionUiType.ENTRY` 的 `canRun()` 恒为 false，因此只负责“打开页面”，
 * 不安装任何 hook；记录 hook 仍由 [FriendHistory] 按开关安装。
 *
 * 页面内集中了好友快照、删除记录、排除名单、导出/导入与最近一次检测信息，
 * 因此这一项也是本分类下所有明细功能的统一入口。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object FriendHistoryEntry : Feature(
    key = "friend_history_entry",
    name = "打开历史好友",
    desc = "查看好友快照、删除记录、排除名单与最近一次检测信息，并可导出/导入数据；" +
            "不受「好友历史记录」开关影响，关闭记录后仍可查看已有数据。",
    uiOrder = 1021,
    uiType = ActionUiType.ENTRY,
) {

    override fun install() = Unit

    /**
     * 入口未读红点：有新的好友变动时提示，打开记录页后清除。
     *
     * 只查一个标记文件，不读整份好友历史 JSON；读不到（无账号 / 非宿主进程）时按无未读处理。
     */
    override fun badgeText(): String? = runCatching {
        if (FriendHistoryStore.hasUnread(QQInterfaces.currentUin)) "有新的好友变动" else null
    }.getOrNull()

    override fun onUiClick(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent()
                .setClassName(HookEnv.hostAppPackageName, ModuleComponents.FRIEND_HISTORY_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.onFailure {
        Log.e("好友历史：打开历史好友页失败", it)
    }.getOrDefault(false)
}
