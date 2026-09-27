package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.runOnce
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo
import mqq.app.MobileQQ
import top.artmoe.inao.item.NewPreventRetractingMessageCore
import java.util.concurrent.atomic.AtomicBoolean

object NTServiceFetcher {

    private lateinit var iKernelService: IKernelService
    private val isMsgHookInitialized = AtomicBoolean(false)

    fun onFetch(service: IKernelService) {
        this.iKernelService = service // initService钩子会被多次调用，允许它重新赋值

        isMsgHookInitialized.runOnce {
            msgPushHook()
        }
    }

    private fun msgPushHook() {
        kernelService.wrapperSession.javaClass.hookMethodBefore(
            "onMsfPush",
            String::class.java,
            ByteArray::class.java,
            PushExtraInfo::class.java
        ) {
            val cmd = it.args[0] as String
            val buffer = it.args[1] as ByteArray

            action(cmd, buffer, it)
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
