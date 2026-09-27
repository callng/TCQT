package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.runOnce
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.message.MsfPushRouter
import com.tencent.qqnt.kernel.api.IKernelService
import mqq.app.MobileQQ
import top.artmoe.inao.item.NewPreventRetractingMessageCore
import java.util.concurrent.atomic.AtomicBoolean

object NTServiceFetcher {

    private lateinit var iKernelService: IKernelService
    private val isMsgHookInitialized = AtomicBoolean(false)

    fun onFetch(service: IKernelService) {
        this.iKernelService = service // initService钩子会被多次调用，允许它重新赋值

        isMsgHookInitialized.runOnce {
            // 推送通道由 MsfPushRouter 集中持有，这里只订阅自己关心的 cmd，
            // 不再独占 hook `onMsfPush`（群事件也要用同一条通道）。
            MsfPushRouter.subscribe { cmd, buffer, param -> action(cmd, buffer, param) }
        }
    }

    private fun action(cmd: String, buffer: ByteArray, param: MethodHookParam) {
        if (!TCQTSetting.getBoolean("msg_anti_recall") ||
            !AntiRecallConfig.hasEnabledReminder()
        ) return

        // 新旧解析器的区别：旧版用 Google Protobuf，新版用 kotlinx-serialization
        val handler: MessageHandler = if (AntiRecallConfig.useNewParser()) {
            NewPreventRetractingMessageCore
        } else {
            AioListener
        }

        when (cmd) {
            "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" ->
                handler.handleInfoSyncPush(buffer, param)
            "trpc.msg.olpush.OlPushService.MsgPush" ->
                handler.handleMsgPush(buffer, param)
        }
    }

    val kernelService: IKernelService
        get() {
            if (!::iKernelService.isInitialized) {
                runCatching {
                    val runtime = MobileQQ.getMobileQQ().peekAppRuntime()
                    if (runtime != null) {
                        val service = runtime.getRuntimeService(IKernelService::class.java, "all")
                        iKernelService = service
                    }
                }
            }
            return iKernelService
        }
}
