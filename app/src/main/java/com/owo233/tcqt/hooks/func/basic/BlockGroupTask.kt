package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class BlockGroupTask : IAction, DexKitTask {

    override val name: String get() = "屏蔽群待办通知"
    override val desc: String get() = "屏蔽群待办消息的通知。"
    override val uiTab: String get() = "基础"
    override val key: String
        get() = "block_group_task"

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
