@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.owo233.tcqt.hooks.entries

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class InfoSyncPush(
    @ProtoNumber(1) val result: UInt? = null,
    @ProtoNumber(2) val errMsg: String? = null,
    @ProtoNumber(3) val pushFlag: UInt = 0u,
    @ProtoNumber(4) val pushSeq: UInt? = null,
    @ProtoNumber(5) val retryFlag: UInt? = null,
    @ProtoNumber(7) val syncContent: SyncContent? = null,
    @ProtoNumber(8) val syncMsgRecall: SyncRecallOperateInfo? = null,
    @ProtoNumber(9) val syncGuildInfo: ByteArray? = null,
    @ProtoNumber(10) val useInitCacheData: UInt? = null
) {

    @Serializable
    data class SyncContent(
        @ProtoNumber(3) val groupSyncContent: List<GroupSyncContent> = emptyList()
    ) {
        @Serializable
        data class GroupSyncContent(
            @ProtoNumber(3) val groupPeerId: Long = 0L,
            @ProtoNumber(4) val startSeq: Int = 0,
            @ProtoNumber(5) val endSeq: Int = 0,
            @ProtoNumber(6) val qqMessage: List<QQMessage> = emptyList()
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
            @ProtoNumber(2) val senderUid: String = "",
            @ProtoNumber(5) val eventTime: Long = 0L,
            @ProtoNumber(8) val msg: List<QQMessage> = emptyList()
        )
    }
}
