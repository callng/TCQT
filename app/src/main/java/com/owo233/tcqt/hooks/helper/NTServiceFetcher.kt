package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList
import com.tencent.qqnt.kernel.api.IKernelService

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var isMsgHookInitialized = false

    fun onFetch(service: IKernelService) {
        this.iKernelService = service // initService钩子会被多次调用，允许它重新赋值

        val key = GeneratedSettingList.MSG_ANTI_RECALL
        if (GeneratedSettingList.getBoolean(key) && !isMsgHookInitialized) {
            msgHook()
            isMsgHookInitialized = true // msgHook方法只需要调用一次
        }
    }

    fun msgHook() {
        kernelService.wrapperSession.javaClass.hookMethod("onMsfPush", beforeHook {
            val cmd = it.args[0] as? String ?: return@beforeHook
            val buffer = it.args[1] as? ByteArray ?: return@beforeHook
            when(cmd) {
                "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                    AioListener.handleInfoSyncPush(buffer, it)
                }
                "trpc.msg.olpush.OlPushService.MsgPush" -> {
                    AioListener.handleMsgPush(buffer, it)
                }
            }
        })
    }

    val kernelService: IKernelService
        get() = iKernelService
}
