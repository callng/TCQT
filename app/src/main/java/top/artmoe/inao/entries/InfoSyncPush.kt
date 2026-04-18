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

// proto有3个字段修饰符required(必选),optional(可选),repeated(重复)
// 咱们的proto文件不使用required,只用optional,因为咱们也不知道哪个字段什么时候才有值,如果是数组一律用repeated修饰.
// 消息只是包含一组类型化字段的聚合。许多标准简单数据类型都可用作字段类型，包括 bool 、 int32 、 float 、 double 和 string
// peerId 就是 uin（是数字），uid是乱七八糟的字母

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InfoSyncPush(
    @ProtoNumber(3) val type: Int = 0, //同步类型
    @ProtoNumber(4) val pushId: Int = 0, // 没啥用的信息貌似
    @ProtoNumber(7) val syncContent: SyncContent? = null, //普通消息同步内容
    @ProtoNumber(8) val syncRecallContent: SyncRecallOperateInfo? = null  // 撤回操作的操作信息
) {
    @Serializable
    data class SyncContent(
        @ProtoNumber(3) val groupSyncContent: List<GroupSyncContent> = emptyList() // 每个群的同步西信息
    ) {
        @Serializable
        data class GroupSyncContent(
            @ProtoNumber(3) val groupPeerId: Long = 0L,  // 群聊的 peerId
            @ProtoNumber(4) val startSeq: Int = 0, //同步的开始seq
            @ProtoNumber(5) val endSeq: Int = 0, // 同步的结束seq
            @ProtoNumber(6) val qqMessage: List<QQMessage> = emptyList()  // 消息数组，数组的数量为 endSeq - startSeq
        )
    }

    @Serializable
    data class SyncRecallOperateInfo(
        @ProtoNumber(3) val syncInfoHead: SyncInfoHead? = null,
        @ProtoNumber(4) val syncInfoBody: List<SyncInfoBody> = emptyList(),
        @ProtoNumber(5) val subHead: SyncInfoHead? = null
    ) {
        @Serializable
        data class SyncInfoHead(
            @ProtoNumber(1) val syncTime: Long = 0L
        )

        @Serializable
        data class SyncInfoBody(
            @ProtoNumber(1) val senderPeerId: Long = 0L,
            @ProtoNumber(5) val eventTime: Long = 0L,
            @ProtoNumber(8) val msgList: List<QQMessage> = emptyList()
        )
    }
}
