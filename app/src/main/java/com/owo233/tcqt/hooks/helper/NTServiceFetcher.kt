@file:OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.data.BuildWrapper
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.utils.logD
import com.owo233.tcqt.utils.logE
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import com.tencent.qqnt.kernel.nativeinterface.SessionTicket
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var curKernelHash = 0

    fun onFetch(service: IKernelService) {
        val msgService = service.msgService ?: return
        val session = service.wrapperSession
        val curHash = service.hashCode() + msgService.hashCode()

        if (isInitForNt(curHash)) return

        curKernelHash = curHash
        this.iKernelService = service

        banBackGround(session)
        initHookOnMsfPush()
        // initHookUpdateTicket()
    }

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    private fun banBackGround(session: IQQNTWrapperSession) {
        runCatching {
            session.javaClass.hookMethod("switchToBackGround").before {
                it.result = Unit
            }

            val service = session.msgService
            service.javaClass.hookMethod("switchBackGroundForMqq").before {
                val cb = it.args[1] as IOperateCallback
                cb.onResult(-1, "injected")
                it.result = Unit
            }

            service.javaClass.hookMethod("switchBackGround").before {
                val cb = it.args[1] as IOperateCallback
                cb.onResult(-1, "injected")
                it.result = Unit
            }
        }.onFailure {
            logE(msg = "try ban backGround failure", cause = it)
        }
    }

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
