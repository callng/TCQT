@file:OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.data.BuildWrapper
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.logD
import com.tencent.qqnt.kernel.api.IKernelService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var curKernelHash = 0

    fun onFetch(service: IKernelService) {
        val msgService = service.msgService ?: return
        val curHash = service.hashCode() + msgService.hashCode()

        if (isInitForNt(curHash)) return

        curKernelHash = curHash
        this.iKernelService = service

        initHookOnMsfPush()
    }

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    private fun initHookOnMsfPush() {
        kernelService.wrapperSession.javaClass.hookMethod("onMsfPush").before { param ->
            val cmd = param.args[0] as String
            val buffer = param.args[1] as ByteArray
            when(cmd) {
                "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                    AioListener.handleInfoSyncPush(buffer, param)
                }
                "trpc.msg.olpush.OlPushService.MsgPush" -> {
                    AioListener.handleMsgPush(buffer, param)
                }
                else -> { }
            }
        }

        if (BuildWrapper.DEBUG) { // 仅供调试
            kernelService.wrapperSession.javaClass.hookMethod("setQimei36").before {
                logD(msg = "setQimei36: ${it.args[0] as String}")
            }
        }
    }

    val kernelService: IKernelService
        get() = iKernelService
}
