package com.owo233.tcqt.features.internal

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.group.GroupEventCore
import com.owo233.tcqt.core.group.RKeyCollector
import com.owo233.tcqt.core.message.MsfPushRouter
import com.owo233.tcqt.host.service.api.GroupService
import com.owo233.tcqt.host.QQInterfaces

/**
 * 群事件桥接：把 core 层的 [GroupEventCore] 接到宿主能力上。
 *
 * core 不能依赖宿主接口，因此这里注入两样东西：
 * - **UID → UIN 解析器**（`GroupService`）；
 * - **MSF 推送转发**（[MsfPushRouter]，与防撤回共用同一条推送通道）。
 *
 * 常驻任务（[InfraTask]）：事件是能力而不是开关，必须随时可用。
 */
@RegisterAction
object GroupEventBridge : InfraTask(
    key = "group_event_bridge",
    priority = ActionPriority.EARLY,
    processes = setOf(ActionProcess.ALL),
) {

    override fun install() {
        GroupEventCore.uidToUinResolver = { uid -> GroupService.getUinFromUid(uid) }
        GroupEventCore.selfUinProvider = { QQInterfaces.currentUin }
        GroupEventCore.install(HookEnv.hostClassLoader)

        // 图片 rkey 没有一次性获取接口，只能从宿主响应里采集
        RKeyCollector.install(HookEnv.hostClassLoader)

        MsfPushRouter.subscribe { command, payload, _ ->
            GroupEventCore.onMsfPush(command, payload)
        }
    }
}
