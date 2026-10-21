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
    fun findsDelayedOriginalOnFourthAttemptInsteadOfFreezingFirstMiss() = runBlocking {
        var calls = 0
        val result = RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(0, if (++calls < 4) emptyList() else listOf(target()))
        }
        assertEquals(4, calls)
        assertEquals(RecallTargetLookup.State.FOUND, result.state)
        kotlin.test.assertTrue("attempts=4" in result.detail)
    }

    @Test
    fun laterQueryFailureDoesNotTurnAnEarlierEmptyPageIntoProofOfAbsence() = runBlocking {
        var calls = 0
        val result = RecallTargetLookup.resolve(recall, retryMillis = 0) {
            RecallTargetLookup.Query(if (++calls == 1) 0 else -1, emptyList())
        }
        assertEquals(RecallTargetLookup.State.UNAVAILABLE, result.state)
        kotlin.test.assertTrue("queryCode=-1" in result.detail)
    }

    @Test
    fun signedAndUnsignedRandomMatchButDifferent64BitValuesDoNot() = runBlocking {
        val r = recall.copy(msgTime = 123, msgRandom = 0xffffffffL)
        val local = target(id = 5678).copy(msgTime = 123, msgRandom = -1)
        assertEquals(RecallTargetLookup.State.FOUND, RecallTargetLookup.resolve(r) {
            RecallTargetLookup.Query(0, listOf(local))
        }.state)
        assertEquals(RecallTargetLookup.State.MISSING, RecallTargetLookup.resolve(r, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(local.copy(msgRandom = 0x1ffffffffL)))
        }.state)
    }

    @Test
    fun groupSequenceAndSenderResolveWhenOptionalLocalMetadataIsNotPopulated() = runBlocking {
        val group = recall.copy(group = true, msgId = 0, msgTime = 123, msgRandom = 456)
        assertEquals(RecallTargetLookup.State.FOUND, RecallTargetLookup.resolve(group) {
            RecallTargetLookup.Query(0, listOf(target()))
        }.state)
        assertEquals(RecallTargetLookup.State.MISSING, RecallTargetLookup.resolve(group, retryMillis = 0) {
            RecallTargetLookup.Query(0, listOf(target().copy(msgTime = 124, msgRandom = 456)))
        }.state)
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

    @Test
    fun navigationFallsBackToProtocolSequenceOnlyWhenLocalRecordIsNotQueryable() {
        val missing = RecallTargetLookup.Result(RecallTargetLookup.State.MISSING)
        val unavailable = RecallTargetLookup.Result(RecallTargetLookup.State.UNAVAILABLE)
        assertNull(RecallTargetLookup.navigationSequence(recall, missing))
        assertEquals(3914L, RecallTargetLookup.navigationSequence(recall, unavailable))
        assertNull(RecallTargetLookup.navigationSequence(recall.copy(msgSeq = 0), unavailable))
    }

    @Test
    fun verifiedHostSequenceTakesPrecedenceOverProtocolSequence() {
        val found = RecallTargetLookup.Result(RecallTargetLookup.State.FOUND, msgSeq = 9876)
        assertEquals(9876L, RecallTargetLookup.navigationSequence(recall, found))
        assertEquals(3914L, RecallTargetLookup.navigationSequence(
            recall,
            RecallTargetLookup.Result(RecallTargetLookup.State.UNAVAILABLE, msgSeq = 0),
        ))
        // A confirmed miss never links, even if a stale local sequence slipped through as 0.
        assertNull(RecallTargetLookup.navigationSequence(
            recall,
            RecallTargetLookup.Result(RecallTargetLookup.State.MISSING, msgSeq = 0),
        ))
    }
}
