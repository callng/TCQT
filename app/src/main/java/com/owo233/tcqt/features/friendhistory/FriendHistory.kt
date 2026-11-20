package com.owo233.tcqt.features.friendhistory

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.QQToastCompat
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.service.FriendHistoryManager

/**
 * 好友历史：记录好友列表变化，入口只放在设置页（不在联系人列表里挂入口）。
 *
 * 默认开启，因为“记录”只有一直跑才有意义；关掉后不再安装检测 hook。
 *
 * 只在主进程安装 hook：好友列表的 JCE 回包与 notifyUI 都在主进程解析，
 * :MSF 里再挂一遍既拿不到数据，又会把同一批 hook 在第二个进程重复装一遍。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object FriendHistory : Feature(
    key = "friend_history",
    name = "好友历史记录",
    desc = "记录好友列表变化，发现被删除的好友。数据按账号保存在模块配置目录，" +
            "排除名单、导出/导入与刷新状态都在同分类的「打开历史好友」页面内，" +
            "同一份排除名单同时作用于记录和通知。",
    uiOrder = 1020,
    defaultEnabled = true,
    actionLabel = "立即刷新好友列表",
) {

    override fun install() {
        FriendHistoryManager.install()
    }

    /**
     * 自证入口：手动拉一次好友列表。
     *
     * 不依赖“下一次真的有人删好友”才能验证，点一下就能看到刷新到底有没有发出去。
     */
    override fun onUiClick(context: Context): Boolean {
        QQToastCompat.info("正在读取好友缓存…")
        ModuleScope.launchIO("好友历史刷新") {
            val message = FriendHistoryManager.manualRefresh()
            ModuleScope.launchMain { QQToastCompat.info(message) }
        }
        return true
    }

    /** 设置页直接显示检测链路状态（hook 是否挂上、上次全量同步、好友数、上次刷新结果）。 */
    override fun getSettingDesc(key: String): String =
        "$desc\n\n${FriendHistoryManager.statusText()}"
}
