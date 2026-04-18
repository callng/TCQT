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
data class MsgPush(
    @ProtoNumber(1) val qqMessage: QQMessage
)
