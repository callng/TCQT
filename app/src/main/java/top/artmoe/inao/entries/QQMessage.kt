/**
 * Copyright 2024-2026 github@Suzhelan,github@leafmoes
 *
 * suzhelan@gmail.com
 *
 * It is forbidden to use the file and this source code for commercial purposes
 * please let me know before modifying the file and source code
 * If your project is open source, please indicate that this feature is from https://github.com/suzhelan/TimTool
 */
package top.artmoe.inao.entries

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber


@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class QQMessage(
    @ProtoNumber(1) val messageHead: MessageHead,
    @ProtoNumber(2) val messageContentInfo: MessageContentInfo,
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
        @ProtoNumber(6) val msgTime: Long = 0L
    )

    @Serializable
    data class MessageBody(
        @ProtoNumber(1) val richMsg: RichMsg? = null,
        @ProtoNumber(2) val operationInfo: ByteArray = byteArrayOf()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MessageBody

            if (richMsg != other.richMsg) return false
            if (!operationInfo.contentEquals(other.operationInfo)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = richMsg?.hashCode() ?: 0
            result = 31 * result + operationInfo.contentHashCode()
            return result
        }

        @Serializable
        data class RichMsg(
            @ProtoNumber(2) val msgContent: List<MsgContent> = emptyList()
        ) {
            @Serializable
            data class MsgContent(
                @ProtoNumber(1) val textMsg: TextMsg? = null,
                @ProtoNumber(16) val msgSender: MsgSender? = null
            ) {
                @Serializable
                data class TextMsg(
                    @ProtoNumber(1) val text: String = ""
                )

                @Serializable
                data class MsgSender(
                    @ProtoNumber(1) val nickName: String = ""
                )
            }
        }

        @Serializable
        data class GroupRecallOperationInfo(
            @ProtoNumber(4) val peerId: Long = 0L,
            @ProtoNumber(11) val info: Info,
            @ProtoNumber(37) val msgSeq: Int = 0
        ) {
            @Serializable
            data class Info(
                @ProtoNumber(1) val operatorUid: String = "",
                @ProtoNumber(3) val msgInfo: MsgInfo
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
            @ProtoNumber(1) val info: Info
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
