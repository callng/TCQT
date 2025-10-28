package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.runOnce
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.PushExtraInfo
import java.util.concurrent.atomic.AtomicBoolean

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private val isMsgHookInitialized = AtomicBoolean(false)

    fun onFetch(service: IKernelService) {
        this.iKernelService = service // initService钩子会被多次调用，允许它重新赋值

        isMsgHookInitialized.runOnce {
            val key = GeneratedSettingList.MSG_ANTI_RECALL
            if (GeneratedSettingList.getBoolean(key)) {
                msgHook()
            }
        }
    }

    fun msgHook() {
        kernelService.wrapperSession.javaClass.hookBeforeMethod(
            "onMsfPush",
            String::class.java,
            ByteArray::class.java,
            PushExtraInfo::class.java
        ) {
            val cmd = it.args[0] as? String ?: return@hookBeforeMethod
            val buffer = it.args[1] as? ByteArray ?: return@hookBeforeMethod
            when(cmd) {
                "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                    AioListener.handleInfoSyncPush(buffer, it)
                }
                "trpc.msg.olpush.OlPushService.MsgPush" -> {
                    AioListener.handleMsgPush(buffer, it)
                }
            }
        }
    }

    val kernelService: IKernelService
        get() = iKernelService
}
