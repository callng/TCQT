package com.owo233.tcqt.internals.helper

import com.tencent.mobileqq.data.troop.TroopMemberInfo
import com.tencent.mobileqq.data.troop.TroopMemberNickInfo
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.troopmemberlist.ITroopMemberListRepoApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal object GroupHelper {

    suspend fun getTroopMemberNickByUin(
        groupId: Long,
        uin: Long
    ): TroopMemberNickInfo? {
        val api = QRoute.api(ITroopMemberListRepoApi::class.java)
        return withTimeoutOrNull(5.seconds) {
            suspendCancellableCoroutine { continuation ->
                runCatching {
                    api.fetchTroopMemberName(
                        groupId.toString(),
                        uin.toString(),
                        "FullBackgroundVM",
                        object : Function1<TroopMemberNickInfo, Unit> {
                            override fun invoke(info: TroopMemberNickInfo) {
                                continuation.resume(info)
                            }
                        }
                    )
                }.onFailure {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun getTroopMemberInfoByUinFromNt(
        groupId: Long,
        uin: Long
    ): TroopMemberInfo? =
        QRoute.api(ITroopMemberListRepoApi::class.java)
            .getTroopMemberFromCacheOrFetchAsync(
                groupId.toString(),
                uin.toString(),
                null,
                "TroopMemberLevelMsgProcessor",
                null
            )

    fun getTroopMemberInfoByUin(
        groupId: Long,
        uin: Long
    ): Result<TroopMemberInfo> = runCatching {
        getTroopMemberInfoByUinFromNt(groupId, uin)
            ?: error("获取群成员信息失败")
    }
}
