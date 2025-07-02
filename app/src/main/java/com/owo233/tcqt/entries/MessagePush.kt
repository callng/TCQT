@file:OptIn(ExperimentalSerializationApi::class)

package com.owo233.tcqt.entries

import com.owo233.tcqt.ext.EMPTY_BYTE_ARRAY
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MessagePush(
    @ProtoNumber(1) var msgBody: Message
)

@Serializable
data class Message(
    @ProtoNumber(1) var msgHead: MessageHead,
    @ProtoNumber(2) var content: MessageContentInfo,
    @ProtoNumber(3) var body: MessageBody? = null
)

@Serializable
data class MessageHead(
    @ProtoNumber(1) val peerId: UInt = UInt.MIN_VALUE,
    @ProtoNumber(2) val peerUid: String? = null,
    @ProtoNumber(5) val targetId: Long = Long.MIN_VALUE,
    @ProtoNumber(6) val targetUid: String? = null
)

@Serializable
data class MessageContentInfo(
    @ProtoNumber(1) val msgType: Int = Int.MIN_VALUE,
    @ProtoNumber(2) val msgSubType: Int = Int.MIN_VALUE,
    @ProtoNumber(3) val subType: Int = Int.MIN_VALUE,
    @ProtoNumber(5) val msgSeq: Int = Int.MIN_VALUE,
    @ProtoNumber(6) val msgTime: Long = Long.MIN_VALUE,
    @ProtoNumber(12) val msgUid: Long = Long.MIN_VALUE,
)

@Serializable
data class MessageBody(
    @ProtoNumber(1) val richMsg: NTRichText? = null,
    @ProtoNumber(2) val msgContent: ByteArray? = EMPTY_BYTE_ARRAY,
)

@Serializable
data class NTRichText(
    @ProtoNumber(2) val elems: ArrayList<Elem>? = null
)

@Serializable
data class Elem(
    @ProtoNumber(1) val text: Text? = null,
)

@Serializable
data class Text(
    @ProtoNumber(1) val text: String? = null,
    @ProtoNumber(12) val resv: ByteArray? = null
)
