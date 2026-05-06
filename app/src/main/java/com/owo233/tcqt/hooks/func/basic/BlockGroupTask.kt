package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
@RegisterSetting(
    key = "block_group_task",
    name = "屏蔽群待办通知",
    type = SettingType.BOOLEAN,
    desc = "屏蔽群待办消息的通知。",
    uiTab = "基础"
)
class BlockGroupTask : IAction, DexKitTask {

    override val key: String
        get() = GeneratedSettingList.BLOCK_GROUP_TASK

    override fun onRun(app: Application, process: ActionProcess) {
        requireMethod("BlockGroupTask").hookBefore { param ->
            val j = param.args[1] as? Long ?: return@hookBefore
            val j2 = param.args[2] as? Long ?: return@hookBefore
            if (j == 528L && j2 == 309L) {
                param.result = null
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "BlockGroupTask" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.notification.modularize")
            matcher {
                paramCount = 5
                usingEqStrings(
                    "TianShuOfflineMsgCenter",
                    "deal0x135Msg online:",
                    "convertMsgCommPB fail: "
                )
            }
        }
    )
}
