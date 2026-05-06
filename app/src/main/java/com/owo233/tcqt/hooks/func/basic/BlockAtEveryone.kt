package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import mqq.app.AppRuntime

@RegisterAction
@RegisterSetting(
    key = "block_at_everyone",
    name = "屏蔽艾特全体通知",
    type = SettingType.BOOLEAN,
    desc = "屏蔽艾特全体消息的通知。",
)
class BlockAtEveryone : IAction {

    override val key: String
        get() = GeneratedSettingList.BLOCK_AT_EVERYONE

    override fun onRun(app: Application, process: ActionProcess) {
        loadOrThrow("com.tencent.qqnt.notification.NotificationFacade").findMethod {
            paramCount = 4
            paramTypes = arrayOf(
                AppRuntime::class.java,
                RecentContactInfo::class.java,
                NotificationCommonInfo::class.java,
                boolean
            )
        }.hookBefore { param ->
            val info = param.args[1] as? RecentContactInfo ?: return@hookBefore
            // atType 1 艾特全体成员 2 艾特群成员 6 艾特自己
            // chatType 2 群组 1 好友
            if (info.chatType == 2 && info.atType == 1) {
                param.result = null
            }
        }
    }
}
