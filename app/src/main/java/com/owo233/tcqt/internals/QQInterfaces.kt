package com.owo233.tcqt.internals

import android.os.Bundle
import com.owo233.tcqt.entries.TrpcOidb
import com.owo233.tcqt.hooks.maple.Maple
import com.owo233.tcqt.internals.msf.MSFHandler
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.PlatformTools.QQ_9_0_70_VER
import com.tencent.common.app.AppInterface
import com.owo233.tcqt.utils.PlatformTools.isMqqPackage
import com.owo233.tcqt.utils.PlatformTools.getQQVersionCode
import com.tencent.mobileqq.pb.ByteStringMicro
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mqq.app.MobileQQ
import tencent.im.oidb.oidb_sso
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class QQInterfaces {
    companion object {
        val app by lazy {
            (if (isMqqPackage())
                MobileQQ.getMobileQQ().waitAppRuntime()
            else
                MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface
        }

        val maple by lazy {
            if (getQQVersionCode() >= QQ_9_0_70_VER) Maple.PublicKernel else Maple.Kernel
        }

        fun sendToServiceMsg(to: ToServiceMsg) {
            app.sendToService(to)
        }

        @OptIn(DelicateCoroutinesApi::class)
        suspend fun sendToServiceMsgAW(
            to: ToServiceMsg,
            timeout: Duration = 30.seconds
        ): FromServiceMsg? {
            val seq = MSFHandler.nextSeq()
            to.addAttribute("tcqt_uid", seq)
            val resp: Pair<ToServiceMsg, FromServiceMsg>? = withTimeoutOrNull(timeout) {
                suspendCancellableCoroutine { continuation ->
                    GlobalScope.launch {
                        MSFHandler.registerResp(seq, continuation)
                        sendToServiceMsg(to)
                    }
                }
            }
            if (resp == null) {
                MSFHandler.unregisterResp(seq)
            }
            return resp?.second
        }

        fun sendExtra(cmd: String, builder: (Bundle) -> Unit) {
            val toServiceMsg = createToServiceMsg(cmd)
            builder(toServiceMsg.extraData)
            app.sendToService(toServiceMsg)
        }

        fun createToServiceMsg(cmd: String): ToServiceMsg {
            return ToServiceMsg("mobileqq.service", app.currentAccountUin, cmd)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun sendOidb(cmd: String, command: Int, service: Int, data: ByteArray, trpc: Boolean = false) {
            val to = createToServiceMsg(cmd)
            if (trpc) {
                val oidb = TrpcOidb(
                    cmd = command,
                    service = service,
                    buffer = data,
                    flag = 1
                )
                to.putWupBuffer(ProtoBuf.encodeToByteArray(oidb))
            } else {
                val oidb = oidb_sso.OIDBSSOPkg()
                oidb.uint32_command.set(command)
                oidb.uint32_service_type.set(service)
                oidb.bytes_bodybuffer.set(ByteStringMicro.copyFrom(data))
                oidb.str_client_version.set(PlatformTools.getClientVersion(MobileQQ.getContext()))
                to.putWupBuffer(oidb.toByteArray())
            }
            to.addAttribute("req_pb_protocol_flag", true)
            app.sendToService(to)
        }

        fun sendBuffer(
            cmd: String,
            isProto: Boolean,
            data: ByteArray,
        ) {
            val toServiceMsg = createToServiceMsg(cmd)
            toServiceMsg.putWupBuffer(data)
            toServiceMsg.addAttribute("req_pb_protocol_flag", isProto)
            sendToServiceMsg(toServiceMsg)
        }

        @DelicateCoroutinesApi
        suspend fun sendBufferAW(
            cmd: String,
            isProto: Boolean,
            data: ByteArray,
            timeout: Duration = 30.seconds
        ): FromServiceMsg? {
            val toServiceMsg = createToServiceMsg(cmd)
            toServiceMsg.putWupBuffer(data)
            toServiceMsg.addAttribute("req_pb_protocol_flag", isProto)
            return sendToServiceMsgAW(toServiceMsg, timeout)
        }

        @OptIn(ExperimentalSerializationApi::class)
        @DelicateCoroutinesApi
        suspend fun sendOidbAW(
            cmd: String,
            command: Int,
            service: Int,
            data: ByteArray,
            trpc: Boolean = false,
            timeout: Duration = 30.seconds
        ): FromServiceMsg? {
            val to = createToServiceMsg(cmd)
            if (trpc) {
                val oidb = TrpcOidb(
                    cmd = command,
                    service = service,
                    buffer = data,
                    flag = 1
                )
                to.putWupBuffer(ProtoBuf.encodeToByteArray(oidb))
            } else {
                val oidb = oidb_sso.OIDBSSOPkg()
                oidb.uint32_command.set(command)
                oidb.uint32_service_type.set(service)
                oidb.bytes_bodybuffer.set(ByteStringMicro.copyFrom(data))
                oidb.str_client_version.set(PlatformTools.getClientVersion(MobileQQ.getContext()))
                to.putWupBuffer(oidb.toByteArray())
            }
            to.addAttribute("req_pb_protocol_flag", true)
            return sendToServiceMsgAW(to, timeout)
        }
    }
}
