package com.owo233.tcqt.host.service

import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.maple.MapleContact
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal object RecallTargetResolver {
    private val visibleTargets = RecallTargetCache()

    fun remember(record: MsgRecord) {
        val target = targetOf(record)
        RecallManager.keysOf(record).forEach { key ->
            if (key.chatType == MsgConstant.KCHATTYPEGROUP || key.chatType == MsgConstant.KCHATTYPEC2C) {
                visibleTargets.remember(key.account, key.chatType == MsgConstant.KCHATTYPEGROUP, key.peerId, target)
            }
        }
    }

    suspend fun resolve(recall: RecallPacketParser.Recall, contact: MapleContact): RecallTargetLookup.Result {
        val account = QQInterfaces.currentUid
        return RecallTargetLookup.resolve(recall) {
            // A message being watched in AIO is already present even if a concurrent
            // database lookup has not caught up. Use its immutable identity snapshot.
            visibleTargets.find(account, recall)?.let {
                return@resolve RecallTargetLookup.Query(0, listOf(it), "visible")
            }
            val service = QQInterfaces.msgService
            if (!recall.group && recall.msgId != 0L) {
                val byId = query { callback ->
                    val ids = arrayListOf(recall.msgId)
                    when (contact) {
                        is MapleContact.Contact -> service.getMsgsByMsgId(contact.inner, ids, callback)
                        is MapleContact.PublicContact -> service.getMsgsByMsgId(contact.inner, ids, callback)
                    }
                }
                if (byId.code == 0 && byId.targets.any { RecallTargetLookup.matches(recall, it) }) {
                    return@resolve byId
                }
            }
            query { callback ->
                val inner = when (contact) {
                    is MapleContact.Contact -> contact.inner
                    is MapleContact.PublicContact -> contact.inner
                }
                // Optional API: resolve the exact signature instead of linking an
                // interface method absent from older hosts. Verified in QQ 9.3.70/9.3.75.
                val method = service.javaClass.getMethod(
                    "getMsgsBySeqAndCount", inner.javaClass,
                    Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    IMsgOperateCallback::class.java,
                )
                // Read a bounded window: a host may return additional records sharing
                // the anchor. RecallTargetLookup still requires the exact identity.
                method.invoke(service, inner, recall.msgSeq, 8, true, false, callback)
            }
        }
    }

    private fun targetOf(record: MsgRecord): RecallTargetLookup.Target = RecallTargetLookup.Target(
        record.msgId, record.msgSeq, record.senderUid.orEmpty(),
        record.msgType != MsgConstant.KMSGTYPENULL && record.msgType != MsgConstant.KMSGTYPEGRAYTIPS &&
            !record.senderUid.isNullOrBlank(),
        record.msgTime, record.msgRandom,
    )

    private suspend fun query(request: (IMsgOperateCallback) -> Unit): RecallTargetLookup.Query =
        suspendCancellableCoroutine { continuation ->
            val answered = AtomicBoolean(false)
            request(IMsgOperateCallback { code, _, records ->
                val targets = records.orEmpty().map(::targetOf)
                // Ignore duplicate callbacks and callbacks arriving after cancellation.
                if (answered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(RecallTargetLookup.Query(code, targets))
                }
            })
        }
}
