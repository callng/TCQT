@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.owo233.tcqt.hooks.entries

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MsgPush(
    @ProtoNumber(1) val qqMessage: QQMessage? = null
)
