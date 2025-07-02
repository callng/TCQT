package com.owo233.tcqt.internals.msf

import com.owo233.tcqt.utils.logE
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

typealias MsfPush = (FromServiceMsg) -> Unit
typealias MsfResp = CancellableContinuation<Pair<ToServiceMsg, FromServiceMsg>>

internal object MSFHandler {
    private val mPushHandlers = hashMapOf<String, MsfPush>()
    private val mRespHandler = hashMapOf<Int, MsfResp>()
    private val mPushLock = Mutex()
    private val mRespLock = Mutex()

    private val seq = atomic(0)

    fun nextSeq(): Int {
        seq.compareAndSet(0xFFFFFFF, 0)
        return seq.incrementAndGet()
    }

    suspend fun registerPush(cmd: String, push: MsfPush) {
        mPushLock.withLock {
            mPushHandlers[cmd] = push
        }
    }

    suspend fun unregisterPush(cmd: String) {
        mPushLock.withLock {
            mPushHandlers.remove(cmd)
        }
    }

    suspend fun registerResp(cmd: Int, resp: MsfResp) {
        mRespLock.withLock {
            mRespHandler[cmd] = resp
        }
    }

    suspend fun unregisterResp(cmd: Int) {
        mRespLock.withLock {
            mRespHandler.remove(cmd)
        }
    }

    fun onPush(fromServiceMsg: FromServiceMsg) {
        val cmd = fromServiceMsg.serviceCmd
        if (cmd == "trpc.msg.olpush.OlPushService.MsgPush") {
            //PrimitiveListener.onPush(fromServiceMsg)
        } else {
            val push = mPushHandlers[cmd]
            push?.invoke(fromServiceMsg)
        }
    }

    fun onResp(toServiceMsg: ToServiceMsg, fromServiceMsg: FromServiceMsg) {
        runCatching {
            val cmd = toServiceMsg.getAttribute("tcqt_uid") as? Int?
                ?: return@runCatching
            val resp = mRespHandler[cmd]
            resp?.resume(toServiceMsg to fromServiceMsg)
        }.onFailure {
            logE(msg = "MSF.onResp failed: ${it.message}")
        }
    }
}
