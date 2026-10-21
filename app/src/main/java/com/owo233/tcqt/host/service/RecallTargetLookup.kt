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
    data class Query(val code: Int, val targets: List<Target>, val source: String = "host")
    enum class State { FOUND, MISSING, UNAVAILABLE }
    data class Result(val state: State, val msgSeq: Long? = null, val detail: String = "")

    /**
     * Prefer the verified local sequence. Only fall back to the protocol sequence when the lookup
     * could not complete: a MISSING result is proof the host never stored the original (e.g. it was
     * delivered while the kernel was silent), so a link would point at nothing.
     */
    fun navigationSequence(recall: RecallPacketParser.Recall, result: Result): Long? =
        result.msgSeq?.takeIf { it > 0 }
            ?: recall.msgSeq.takeIf { it > 0 && result.state == State.UNAVAILABLE }

    suspend fun resolve(
        recall: RecallPacketParser.Recall,
        timeoutMillis: Long = 2500,
        retryMillis: Long = 150,
        query: suspend () -> Query,
    ): Result {
        return try {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                var lastCode = 0
                var detail = ""
                repeat(4) { attempt ->
                    if (attempt > 0) delay((retryMillis * (1L shl (attempt - 1))).milliseconds)
                    val response = query()
                    lastCode = response.code
                    detail = diagnostics(recall, response, attempt + 1)
                    if (response.code == 0) {
                        val target = response.targets.firstOrNull { matches(recall, it) }
                        if (target != null) return@withTimeoutOrNull Result(State.FOUND, target.msgSeq, detail)
                    }
                }
                Result(if (lastCode == 0) State.MISSING else State.UNAVAILABLE, detail = detail)
            } ?: Result(State.UNAVAILABLE, detail = "timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(State.UNAVAILABLE, detail = e.javaClass.simpleName)
        }
    }

    internal fun matches(recall: RecallPacketParser.Recall, target: Target): Boolean {
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
            (recall.msgTime == 0L || (recall.group && target.msgTime == 0L) || target.msgTime == recall.msgTime) &&
            (recall.msgRandom == 0L || (recall.group && target.msgRandom == 0L) ||
                unsignedRandom(target.msgRandom) == unsignedRandom(recall.msgRandom))
    }

    private fun unsignedRandom(value: Long): Long =
        if (value in Int.MIN_VALUE.toLong()..0xffffffffL) value and 0xffffffffL else value

    private fun diagnostics(recall: RecallPacketParser.Recall, query: Query, attempts: Int): String {
        val sameSeq = query.targets.filter { it.msgSeq == recall.msgSeq }
        return "source=${query.source} attempts=$attempts queryCode=${query.code} count=${query.targets.size} " +
            "sameSeq=${sameSeq.size} original=${sameSeq.count { it.originalMessage }} " +
            "senderMatch=${sameSeq.count { it.senderUid == recall.senderUid }} " +
            "timeMatch=${sameSeq.count { it.msgTime == recall.msgTime }} " +
            "randomMatch=${sameSeq.count { unsignedRandom(it.msgRandom) == unsignedRandom(recall.msgRandom) }}"
    }
}
