@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.owo233.tcqt.host.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/** Minimal protobuf projection: rich text, profiles and unknown binary data are never decoded as strings.
 * These models are decode-only. Re-encoding them would discard unknown QQ fields.
 */
internal object KotlinxRecallMetadataDecoder : RecallMetadataDecoder {
    override fun message(bytes: ByteArray): RecallMetadataDecoder.Message {
        val msg = ProtoBuf.decodeFromByteArray<RecallMessage>(bytes)
        return RecallMetadataDecoder.Message(
            msg.head.senderUid.recallUid(), msg.head.receiverUid.recallUid(),
            msg.content.type, msg.content.subtype, msg.content.subSeq, msg.body.operation,
        )
    }

    override fun c2c(bytes: ByteArray): List<RecallMetadataDecoder.C2C> =
        ProtoBuf.decodeFromByteArray<C2COperation>(bytes).infos.map {
            RecallMetadataDecoder.C2C(
                it.operatorUid.recallUid(), it.receiverUid.recallUid(),
                it.msgSeq.toUInt().toLong(), it.senderMsgSeq.toUInt().toLong(), it.msgId, it.msgTime, it.msgRandom,
            )
        }

    override fun group(bytes: ByteArray): RecallMetadataDecoder.Group {
        val op = ProtoBuf.decodeFromByteArray<GroupOperation>(bytes)
        return RecallMetadataDecoder.Group(op.peerId, op.info.operatorUid.recallUid(),
            op.info.targets.map {
                RecallMetadataDecoder.Target(
                    it.msgSeq.toUInt().toLong(), it.senderUid.recallUid(), it.msgTime, it.msgRandom,
                )
            })
    }

    @Serializable
    private data class RecallMessage(
        @ProtoNumber(1) val head: Head = Head(),
        @ProtoNumber(2) val content: Content = Content(),
        @ProtoNumber(3) val body: Body = Body(),
    )

    @Serializable
    private data class Head(
        @ProtoNumber(2) val senderUid: ByteArray = byteArrayOf(),
        @ProtoNumber(6) val receiverUid: ByteArray = byteArrayOf(),
    )

    @Serializable
    private data class Content(
        @ProtoNumber(1) val type: Int = 0,
        @ProtoNumber(2) val subtype: Int = 0,
        @ProtoNumber(3) val subSeq: Int = 0,
    )

    @Serializable
    private data class Body(@ProtoNumber(2) val operation: ByteArray = byteArrayOf())

    @Serializable
    private data class C2COperation(@ProtoNumber(1) val infos: List<C2CInfo> = emptyList())

    @Serializable
    private data class C2CInfo(
        @ProtoNumber(1) val operatorUid: ByteArray = byteArrayOf(),
        @ProtoNumber(2) val receiverUid: ByteArray = byteArrayOf(),
        @ProtoNumber(3) val senderMsgSeq: Int = 0,
        @ProtoNumber(4) val msgId: Long = 0,
        @ProtoNumber(5) val msgTime: Long = 0,
        @ProtoNumber(6) val msgRandom: Long = 0,
        @ProtoNumber(20) val msgSeq: Int = 0,
    )

    @Serializable
    private data class GroupOperation(
        @ProtoNumber(4) val peerId: Long = 0,
        @ProtoNumber(11) val info: GroupInfo = GroupInfo(),
    )

    @Serializable
    private data class GroupInfo(
        @ProtoNumber(1) val operatorUid: ByteArray = byteArrayOf(),
        @ProtoNumber(3) val targets: List<GroupTarget> = emptyList(),
    )

    @Serializable
    private data class GroupTarget(
        @ProtoNumber(1) val msgSeq: Int = 0,
        @ProtoNumber(2) val msgTime: Long = 0,
        @ProtoNumber(3) val msgRandom: Long = 0,
        @ProtoNumber(6) val senderUid: ByteArray = byteArrayOf(),
    )
}
