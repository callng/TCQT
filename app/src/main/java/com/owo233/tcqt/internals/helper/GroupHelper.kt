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
                    api.fetchTroopMemberName(groupId.toString(), uin.toString(), null, groupId.toString()) {
                        continuation.resume(it)
                    }
                }.onFailure {
                    continuation.resume(null)
                }
            }
        }
    }

    fun getTroopMemberInfoByUinFromNt(
        groupId: Long,
        uin: Long
    ): Result<TroopMemberInfo> {
        return runCatching {
            val api = QRoute.api(ITroopMemberListRepoApi::class.java)
            api.getTroopMemberFromCacheOrFetchAsync(
                groupId.toString(),
                uin.toString(),
                null,
                "AIONickBlockApiImpl-level",
                null
            ) ?: throw Exception("获取群成员信息失败")
        }
    }

    fun getTroopMemberInfoByUin(
        groupId: Long,
        uin: Long
    ): Result<TroopMemberInfo> {
        val info = getTroopMemberInfoByUinFromNt(groupId, uin).getOrNull()
        return if (info != null) {
            Result.success(info)
        } else {
            Result.failure(Exception("获取群成员信息失败"))
        }
    }
}
