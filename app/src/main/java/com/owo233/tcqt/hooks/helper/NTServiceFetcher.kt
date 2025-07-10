@file:OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.owo233.tcqt.hooks.helper

import com.google.protobuf.UnknownFieldSet
import com.owo233.tcqt.data.BuildWrapper
import com.owo233.tcqt.entries.MessagePush
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.logD
import com.owo233.tcqt.utils.logE
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.SessionTicket
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

        initHookOnMsfPush()
        initHookUpdateTicket()
    }

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    private fun initHookOnMsfPush() {
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

        if (BuildWrapper.DEBUG) { // 仅供调试
            kernelService.wrapperSession.javaClass.hookMethod("setQimei36").before {
                logD(msg = "setQimei36: ${it.args[0] as String}")
            }
        }
    }

    private fun initHookUpdateTicket() {
        if (BuildWrapper.DEBUG) {
            kernelService.wrapperSession.javaClass.hookMethod("updateTicket").before { // 仅供调试
                val ticket = it.args[0] as SessionTicket

                val a2 = ticket.a2
                val d2 = ticket.d2
                val d2Key = ticket.d2Key

                logD(msg = """
                updateTicket:
                a2: $a2
                d2: $d2
                d2Key: $d2Key
            """.trimIndent())
            }
        }
    }

    val kernelService: IKernelService
        get() = iKernelService
}
