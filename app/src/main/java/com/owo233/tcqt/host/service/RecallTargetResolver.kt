package com.owo233.tcqt.host.service

import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal object RecallTargetResolver {
    suspend fun resolve(recall: RecallPacketParser.Recall, contact: MapleContact): RecallTargetLookup.Result =
        RecallTargetLookup.resolve(recall) {
            val service = QQInterfaces.msgService
            if (!recall.group && recall.msgId != 0L) {
                val byId = query { callback ->
                    val ids = arrayListOf(recall.msgId)
                    when (contact) {
                        is MapleContact.Contact -> service.getMsgsByMsgId(contact.inner, ids, callback)
                        is MapleContact.PublicContact -> service.getMsgsByMsgId(contact.inner, ids, callback)
                    }
                }
                if (byId.code == 0 && byId.targets.any { it.msgId == recall.msgId }) {
                    return@resolve byId
                }
            }
            query { callback ->
                val inner = when (contact) {
                    is MapleContact.Contact -> contact.inner
                    is MapleContact.PublicContact -> contact.inner
                }
                // Optional API: resolve the exact signature instead of linking an
                // interface method absent from older hosts. Verified in QQ 9.3.75.
                val method = service.javaClass.getMethod(
                    "getMsgsBySeqAndCount", inner.javaClass,
                    Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    IMsgOperateCallback::class.java,
                )
                method.invoke(service, inner, recall.msgSeq, 1, true, false, callback)
            }
        }

    private suspend fun query(request: (IMsgOperateCallback) -> Unit): RecallTargetLookup.Query =
        suspendCancellableCoroutine { continuation ->
            val answered = AtomicBoolean(false)
            request(IMsgOperateCallback { code, _, records ->
                val targets = records.orEmpty().map { record ->
                    RecallTargetLookup.Target(
                        record.msgId, record.msgSeq, record.senderUid.orEmpty(),
                        record.msgType != MsgConstant.KMSGTYPENULL && record.msgType != MsgConstant.KMSGTYPEGRAYTIPS,
                        record.msgTime, record.msgRandom,
                    )
                }
                // Ignore duplicate callbacks and callbacks arriving after cancellation.
                if (answered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(RecallTargetLookup.Query(code, targets))
                }
            })
        }
}
