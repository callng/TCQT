@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.owo233.tcqt.hooks.entries

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class QQMessage(
    @ProtoNumber(1) val messageHead: MessageHead? = null,
    @ProtoNumber(2) val messageContentInfo: MessageContentInfo? = null,
    @ProtoNumber(3) val messageBody: MessageBody? = null
) {

    @Serializable
    data class MessageHead(
        @ProtoNumber(1) val senderPeerId: Long = 0L,
        @ProtoNumber(2) val senderUid: String = "",
        @ProtoNumber(5) val receiverPeerId: Long = 0L,
        @ProtoNumber(6) val receiverUid: String = "",
        @ProtoNumber(8) val senderInfo: SenderInfo? = null
    ) {
        @Serializable
        data class SenderInfo(
            @ProtoNumber(1) val peerId: Long = 0L,
            @ProtoNumber(2) val msgSubType: Int = 0,
            @ProtoNumber(4) val nickName: String = ""
        )
    }

    @Serializable
    data class MessageContentInfo(
        @ProtoNumber(1) val msgType: Int = 0,
        @ProtoNumber(2) val msgSubType: Int = 0,
        @ProtoNumber(3) val subSeq: Int = 0,
        @ProtoNumber(5) val msgSeq: Int = 0,
        @ProtoNumber(6) val msgTime: Long = 0L,
        @ProtoNumber(11) val msgSeqId: Int = 0
    )

    @Serializable
    data class MessageBody(
        @ProtoNumber(1) val richMsg: RichMsg? = null,
        @ProtoNumber(2) val operationInfo: ByteArray? = null
    ) {

        @Serializable
        data class RichMsg(
            @ProtoNumber(2) val msgContent: List<MsgContent> = emptyList()
        ) {
            @Serializable
            data class MsgContent(
                @ProtoNumber(1) val textMsg: TextMsg? = null,
                @ProtoNumber(16) val msgSender: MsgSender? = null,
                @ProtoNumber(53) val myCustomField: Mt? = null
            ) {
                @Serializable
                data class TextMsg(
                    @ProtoNumber(1) val text: String = ""
                )

                @Serializable
                data class MsgSender(
                    @ProtoNumber(1) val nickName: String = ""
                )

                @Serializable
                data class Mt(
                    @ProtoNumber(1) val mtType: Int = 0
                )
            }
        }

        @Serializable
        data class GroupRecallOperationInfo(
            @ProtoNumber(4) val peerId: Long = 0L,
            @ProtoNumber(11) val info: Info? = null,
            @ProtoNumber(37) val msgSeq: Int = 0
        ) {
            @Serializable
            data class Info(
                @ProtoNumber(1) val operatorUid: String = "",
                @ProtoNumber(3) val msgInfo: MsgInfo? = null
            ) {
                @Serializable
                data class MsgInfo(
                    @ProtoNumber(1) val msgSeq: Int = 0,
                    @ProtoNumber(2) val msgTime: Long = 0L,
                    @ProtoNumber(6) val senderUid: String = ""
                )
            }
        }

        @Serializable
        data class C2CRecallOperationInfo(
            @ProtoNumber(1) val info: Info? = null
        ) {
            @Serializable
            data class Info(
                @ProtoNumber(1) val operatorUid: String = "",
                @ProtoNumber(2) val receiverUid: String = "",
                @ProtoNumber(5) val msgTime: Long = 0L,
                @ProtoNumber(6) val msgRandom: Long = 0L,
                @ProtoNumber(20) val msgSeq: Int = 0
            )
        }
    }
}
