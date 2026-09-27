package com.owo233.tcqt.features.internal

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.message.KernelServiceReady

/**
 * 内核服务就绪总线的常驻安装点。
 *
 * 各个功能不再各自 hook `KernelServiceImpl.initService`：本任务在全进程装一次，
 * 之后的订阅（消息分发器、防撤回的推送解析…）都由 [KernelServiceReady] 转发。
 */
@RegisterAction
object KernelMessageBridge : InfraTask(
    key = "kernel_message_bridge",
    priority = ActionPriority.EARLY,
    processes = setOf(ActionProcess.ALL),
) {

    override fun install() {
        KernelServiceReady.installHook()
    }
}
