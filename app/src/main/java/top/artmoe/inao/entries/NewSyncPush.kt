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
data class NewSyncPush(
    @ProtoNumber(3) val type: Int = 0, //同步类型
    @ProtoNumber(4) val pushId: Int = 0, // 没啥用的信息貌似
    @ProtoNumber(7) val syncContent: ByteArray? = null, //普通消息同步内容
    @ProtoNumber(8) val syncRecallContent: SyncRecallOperateInfo? = null,  // 撤回操作的操作信息
    @ProtoNumber(9) val syncPushExtra: ByteArray? = null,
) {
    @Serializable
    data class SyncRecallOperateInfo(
        @ProtoNumber(3) val syncInfoHead: SyncInfoHead? = null,
        @ProtoNumber(4) val syncInfoBody: List<ByteArray>? = null,
        @ProtoNumber(5) val subHead: SyncInfoHead? = null,
        @ProtoNumber(6) val unknownFlag: Int = 0,
    ) {
        @Serializable
        data class SyncInfoHead(
            @ProtoNumber(1) val syncTime: Long = 0L,
        )
    }
}
