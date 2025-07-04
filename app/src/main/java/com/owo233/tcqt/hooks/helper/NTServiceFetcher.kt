@file:OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.owo233.tcqt.hooks.helper

import com.google.protobuf.UnknownFieldSet
import com.owo233.tcqt.entries.MessagePush
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.logE
import com.tencent.qqnt.kernel.api.IKernelService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var curKernelHash = 0

    fun onFetch(service: IKernelService) {
        val msgService = service.msgService ?: return
        val curHash = service.hashCode() + msgService.hashCode()
        if (isInitForNt(curHash)) return

        curKernelHash = curHash
        this.iKernelService = service
        initNTKernel()
    }

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    private fun initNTKernel() {
        kernelService.wrapperSession.javaClass.hookMethod("onMsfPush").before {
            runCatching {
                val cmd = it.args[0] as String
                val buffer = it.args[1] as? ByteArray ?: return@before
                when(cmd) {
                    "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                        val unknownFieldSet = UnknownFieldSet.parseFrom(buffer)
                        AioListener.onInfoSyncPush(unknownFieldSet).onSuccess { new ->
                            it.args[1] = new.toByteArray()
                        }.onFailure { e ->
                            logE(msg = "无法处理InfoSyncPush", cause = e)
                        }
                    }
                    "trpc.msg.olpush.OlPushService.MsgPush" -> {
                        val msgPush = ProtoBuf.decodeFromByteArray<MessagePush>(buffer)
                        if (AioListener.onMsgPush(msgPush)) {
                            it.result = Unit
                        } else {
                            return@before
                        }
                    }
                    else -> { }
                }
            }
        }
    }

    val kernelService: IKernelService
        get() = iKernelService
}
