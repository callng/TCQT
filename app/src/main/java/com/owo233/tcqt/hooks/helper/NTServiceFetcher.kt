package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList
import com.tencent.qqnt.kernel.api.IKernelService

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var curKernelHash = 0

    fun onFetch(service: IKernelService) {
        val session = service.wrapperSession ?: return
        val curHash = service.hashCode() + session.hashCode()

        if (isInitForNt(curHash)) return

        curKernelHash = curHash
        this.iKernelService = service

        val key = GeneratedSettingList.MSG_ANTI_RECALL
        if (GeneratedSettingList.getBoolean(key)) {
            msgHook()
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

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    val kernelService: IKernelService
        get() = iKernelService
}
