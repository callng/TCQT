@file:OptIn(ExperimentalSerializationApi::class)

package com.owo233.tcqt.entries

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class C2CRecallMessage(
    @ProtoNumber(1) val info: C2cRecallMsgInfo
)

@Serializable
data class C2cRecallMsgInfo(
    @ProtoNumber(1) val senderUid: String,
    @ProtoNumber(2) val receiverUid: String,
    @ProtoNumber(3) val msgClientSeq: Long = Long.MIN_VALUE,
    @ProtoNumber(4) val msgUid: Long = Long.MIN_VALUE,
    @ProtoNumber(5) val msgTime: Long = Long.MIN_VALUE,

    @ProtoNumber(13) val wording: RecallWording? = null,
    @ProtoNumber(20) val msgSeq: Long = Long.MIN_VALUE,
)

@Serializable
data class GroupRecallMessage(
    @ProtoNumber(1) val type: UInt,
    @ProtoNumber(4) val peerId: ULong,
    @ProtoNumber(11) val operation: GroupRecallOperation,
)

@Serializable
data class GroupRecallOperation(
    @ProtoNumber(1) val operatorUid: String? = null,
    @ProtoNumber(3) val msgInfo: GroupRecallMsgInfo? = null,
    @ProtoNumber(9) val wording: RecallWording? = null
)

@Serializable
data class GroupRecallMsgInfo(
    @ProtoNumber(1) val msgSeq: Long = Long.MIN_VALUE,
    @ProtoNumber(2) val msgTime: Long = Long.MIN_VALUE,
    @ProtoNumber(6) val senderUid: String? = null
)

@Serializable
data class RecallWording(
    @ProtoNumber(2) val wording: String? = null
)
