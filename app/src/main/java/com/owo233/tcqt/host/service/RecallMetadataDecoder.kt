package com.owo233.tcqt.host.service

import top.artmoe.inao.entries.RecallPushOuterClass

/** Typed decoding is independent of wire filtering and host-side message lookup. */
internal interface RecallMetadataDecoder {
    data class Message(
        val senderUid: String,
        val receiverUid: String,
        val type: Int,
        val subtype: Int,
        val subSeq: Int,
        val operation: ByteArray,
    )

    data class C2C(
        val operatorUid: String,
        val receiverUid: String,
        val msgSeq: Long,
        val senderMsgSeq: Long,
        val msgId: Long,
        val msgTime: Long,
        val msgRandom: Long,
    )

    data class Group(val peerId: Long, val operatorUid: String, val targets: List<Target>)
    data class Target(val msgSeq: Long, val senderUid: String, val msgTime: Long, val msgRandom: Long)

    fun message(bytes: ByteArray): Message
    fun c2c(bytes: ByteArray): List<C2C>
    fun group(bytes: ByteArray): Group
}

internal fun ByteArray.recallUid(): String =
    runCatching { decodeToString(throwOnInvalidSequence = true) }.getOrDefault("")

internal object GoogleRecallMetadataDecoder : RecallMetadataDecoder {
    override fun message(bytes: ByteArray): RecallMetadataDecoder.Message {
        val msg = RecallPushOuterClass.RecallMessage.parseFrom(bytes)
        return RecallMetadataDecoder.Message(
            msg.head.senderUid.toByteArray().recallUid(), msg.head.receiverUid.toByteArray().recallUid(),
            msg.content.msgType, msg.content.msgSubType, msg.content.subSeq, msg.body.operationInfo.toByteArray(),
        )
    }

    override fun c2c(bytes: ByteArray): List<RecallMetadataDecoder.C2C> =
        RecallPushOuterClass.C2CRecall.parseFrom(bytes).infoList.map {
            RecallMetadataDecoder.C2C(
                it.operatorUid.toByteArray().recallUid(), it.receiverUid.toByteArray().recallUid(),
                it.msgSeq.toUInt().toLong(), it.senderMsgSeq.toUInt().toLong(), it.msgId, it.msgTime, it.msgRandom,
            )
        }

    override fun group(bytes: ByteArray): RecallMetadataDecoder.Group {
        val op = RecallPushOuterClass.GroupRecall.parseFrom(bytes)
        return RecallMetadataDecoder.Group(op.peerId, op.info.operatorUid.toByteArray().recallUid(),
            op.info.msgInfoList.map {
                RecallMetadataDecoder.Target(
                    it.msgSeq.toUInt().toLong(), it.senderUid.toByteArray().recallUid(), it.msgTime, it.msgRandom,
                )
            })
    }
}
