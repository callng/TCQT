package com.owo233.tcqt.host.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** Keeps a successful query with no target distinct from a query that could not complete. */
internal object RecallTargetLookup {
    data class Target(
        val msgId: Long,
        val msgSeq: Long,
        val senderUid: String,
        val originalMessage: Boolean = true,
        val msgTime: Long = 0,
        val msgRandom: Long = 0,
    )
    data class Query(val code: Int, val targets: List<Target>)
    enum class State { FOUND, MISSING, UNAVAILABLE }
    data class Result(val state: State, val msgSeq: Long? = null, val detail: String = "")

    suspend fun resolve(
        recall: RecallPacketParser.Recall,
        timeoutMillis: Long = 1500,
        retryMillis: Long = 150,
        query: suspend () -> Query,
    ): Result {
        return try {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                var completed = false
                var lastCode = 0
                repeat(2) { attempt ->
                    if (attempt > 0) delay(retryMillis.milliseconds)
                    val response = query()
                    lastCode = response.code
                    if (response.code == 0) {
                        completed = true
                        val target = response.targets.firstOrNull { matches(recall, it) }
                        if (target != null) return@withTimeoutOrNull Result(State.FOUND, target.msgSeq)
                    }
                }
                Result(if (completed) State.MISSING else State.UNAVAILABLE, detail = "queryCode=$lastCode")
            } ?: Result(State.UNAVAILABLE, detail = "timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(State.UNAVAILABLE, detail = e.javaClass.simpleName)
        }
    }

    private fun matches(recall: RecallPacketParser.Recall, target: Target): Boolean {
        if (target.msgSeq <= 0 || !target.originalMessage) return false
        // Message IDs are authoritative for C2C. Never accept a nearby sequence as the target.
        if (!recall.group && recall.msgId != 0L) {
            if (target.msgId == recall.msgId) return true
            // Some hosts assign a different local ID. Only accept that fallback when
            // sequence, sender, timestamp and random identify the same original message.
            if (recall.msgTime == 0L || recall.msgRandom == 0L) return false
        }
        return target.msgSeq == recall.msgSeq &&
            (recall.senderUid.isBlank() || target.senderUid == recall.senderUid) &&
            (recall.msgTime == 0L || target.msgTime == recall.msgTime) &&
            (recall.msgRandom == 0L || target.msgRandom == recall.msgRandom)
    }
}
