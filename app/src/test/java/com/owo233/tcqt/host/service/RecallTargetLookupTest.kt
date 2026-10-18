package com.owo233.tcqt.host.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecallTargetLookupTest {
    private val recall = RecallPacketParser.Recall(false, "u_peer", 3914, "u_peer", "u_peer", 1234, 35550)
    private fun target(id: Long = 1234, seq: Long = 3914, original: Boolean = true) =
        RecallTargetLookup.Target(id, seq, "u_peer", original)

    @Test
    fun returnsHostSequenceFromExactMessageIdEvenWhenWireSequencesDiffer() = runBlocking {
        val result = RecallTargetLookup.resolve(recall) {
            RecallTargetLookup.Query(0, listOf(target(seq = 9876)))
        }
        assertEquals(RecallTargetLookup.State.FOUND, result.state)
        assertEquals(9876L, result.msgSeq)
    }

    @Test
    fun doesNotLinkNearbyMessageWithSameSequenceButDifferentId() = runBlocking {
        val result = RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(target(id = 4321)))
        }
        assertEquals(RecallTargetLookup.State.MISSING, result.state)
        assertNull(result.msgSeq)
    }

    @Test
    fun differentLocalIdRequiresMatchingSenderTimestampRandomAndReceiverSequence() = runBlocking {
        val withIdentity = recall.copy(msgTime = 1792327665, msgRandom = 1779371984)
        val local = target(id = 5678).copy(msgTime = withIdentity.msgTime, msgRandom = withIdentity.msgRandom)
        assertEquals(3914L, RecallTargetLookup.resolve(withIdentity) {
            RecallTargetLookup.Query(0, listOf(local))
        }.msgSeq)
        assertEquals(RecallTargetLookup.State.MISSING, RecallTargetLookup.resolve(withIdentity, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(local.copy(msgRandom = 1)))
        }.state)
    }

    @Test
    fun retriesMessageNotYetCommittedByTheHost() = runBlocking {
        var calls = 0
        val result = RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(0, if (++calls == 1) emptyList() else listOf(target()))
        }
        assertEquals(2, calls)
        assertEquals(RecallTargetLookup.State.FOUND, result.state)
    }

    @Test
    fun emptySuccessIsDifferentFromFailureOrUnsupportedApi() = runBlocking {
        assertEquals(RecallTargetLookup.State.MISSING, RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(0, emptyList())
        }.state)
        assertEquals(RecallTargetLookup.State.UNAVAILABLE, RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(-1, emptyList())
        }.state)
        assertEquals(RecallTargetLookup.State.UNAVAILABLE, RecallTargetLookup.resolve(recall) {
            throw NoSuchMethodException("getMsgsBySeqAndCount")
        }.state)
    }

    @Test
    fun callbackTimeoutCannotHangGrayTipCreation() = runBlocking {
        val result = RecallTargetLookup.resolve(recall, timeoutMillis = 30) { awaitCancellation() }
        assertEquals(RecallTargetLookup.State.UNAVAILABLE, result.state)
        assertNull(result.msgSeq)
    }

    @Test
    fun parentCancellationPropagates() = runBlocking {
        assertFailsWith<CancellationException> {
            RecallTargetLookup.resolve(recall) { throw CancellationException("account changed") }
        }
        Unit
    }

    @Test
    fun grayTipOrDeletedPlaceholderIsNotTheOriginalMessage() = runBlocking {
        val result = RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(target(original = false)))
        }
        assertEquals(RecallTargetLookup.State.MISSING, result.state)
    }

    @Test
    fun groupRequiresExactTargetSequenceAndSender() = runBlocking {
        val group = recall.copy(group = true, peerId = "123456", msgSeq = 415, msgId = 0)
        val result = RecallTargetLookup.resolve(group, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(
                target(seq = 416),
                target(seq = 415).copy(senderUid = "u_someone_else"),
            ))
        }
        assertEquals(RecallTargetLookup.State.MISSING, result.state)
        assertEquals(415L, RecallTargetLookup.resolve(group) {
            RecallTargetLookup.Query(0, listOf(target(seq = 415)))
        }.msgSeq)
    }
}
