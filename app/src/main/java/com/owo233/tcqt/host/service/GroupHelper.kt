package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.reflect.hostFunction
import com.tencent.mobileqq.data.troop.TroopMemberNickInfo
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.troopmemberlist.ITroopMemberListRepoApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal object GroupHelper {

    private const val CACHE_EXPIRY_MS = 30L * 60L * 1000L // 将结果缓存30分钟

    private val nickCache = ConcurrentHashMap<String, NickCacheEntry>()

    private val repoClass by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadOrThrow("com.tencent.qqnt.troopmemberlist.TroopMemberListRepo")
    }

    private val fetchNickMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
        repoClass.declaredMethods
            .first {
                it.name == "fetchTroopMemberName" && it.parameterCount == 4
            }
            .apply { isAccessible = true }
    }

    private val repoInstance by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val api = QRoute.api(ITroopMemberListRepoApi::class.java)
        api.javaClass.declaredFields
            .firstOrNull { it.type == repoClass }
            ?.apply { isAccessible = true }
            ?.get(api)
            ?: error("无法获取 TroopMemberListRepo 实例")
    }

    private fun cacheKey(groupId: Long, uin: Long): String = "${groupId}_$uin"

    suspend fun getTroopMemberNickByUin(
        groupId: Long,
        uin: Long
    ): TroopMemberNickInfo? {

        val key = cacheKey(groupId, uin)
        val now = System.currentTimeMillis()

        nickCache[key]?.let {
            if (now - it.timestamp < CACHE_EXPIRY_MS) {
                return it.info
            }
            nickCache.remove(key)
        }

        val result = withTimeoutOrNull(5.seconds) {
            suspendCancellableCoroutine { cont ->
                val callback = hostFunction<TroopMemberNickInfo?>(
                    fetchNickMethod.parameterTypes[3]
                ) { info ->
                    info?.takeIf { cont.isActive }?.also { cont.resume(it) }
                }
                runCatching {
                    fetchNickMethod.invoke(
                        repoInstance,
                        groupId.toString(),
                        uin.toString(),
                        "FullBackgroundVM",
                        callback
                    )
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (result != null) {
            nickCache[key] = NickCacheEntry(result)
        }
        return result
    }
}

private data class NickCacheEntry(
    val info: TroopMemberNickInfo,
    val timestamp: Long = System.currentTimeMillis()
)
