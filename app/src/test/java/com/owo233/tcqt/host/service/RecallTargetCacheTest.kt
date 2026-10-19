package com.owo233.tcqt.host.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecallTargetCacheTest {
    private val recall = RecallPacketParser.Recall(true, "123456", 410, "u_actor", "u_actor")
    private fun target(seq: Long = 410) = RecallTargetLookup.Target(1234, seq, "u_actor")

    @Test
    fun visibleOriginalSurvivesTemporaryHostQueryMissWithoutSharingAcrossAccountsOrChats() {
        val cache = RecallTargetCache()
        cache.remember("u_self", true, "123456", target())
        assertEquals(410L, cache.find("u_self", recall)?.msgSeq)
        assertNull(cache.find("u_other", recall))
        assertNull(cache.find("u_self", recall.copy(peerId = "654321")))
        assertNull(cache.find("u_self", recall.copy(group = false)))
        assertNull(cache.find("u_self", recall.copy(msgSeq = 411)))
        assertNull(cache.find("u_self", recall.copy(senderUid = "u_else")))
    }

    @Test
    fun snapshotsExpireAndOldestEntriesAreBounded() {
        var now = 0L
        val cache = RecallTargetCache(capacity = 2, ttlNanos = 100) { now }
        cache.remember("u_self", true, "123456", target(410))
        cache.remember("u_self", true, "123456", target(411))
        cache.remember("u_self", true, "123456", target(412))
        assertNull(cache.find("u_self", recall))
        assertEquals(411L, cache.find("u_self", recall.copy(msgSeq = 411))?.msgSeq)
        now = 100
        assertNull(cache.find("u_self", recall.copy(msgSeq = 411)))
    }

    @Test
    fun placeholderCannotReplaceAnObservedOriginal() {
        val cache = RecallTargetCache()
        cache.remember("u_self", true, "123456", target())
        cache.remember("u_self", true, "123456", target().copy(originalMessage = false))
        cache.remember("u_self", true, "123456", target().copy(senderUid = ""))
        assertEquals(target(), cache.find("u_self", recall))
    }

    @Test
    fun c2cUsesExactIdEvenWhenTheLocalSequenceDiffers() {
        val cache = RecallTargetCache()
        cache.remember("u_self", false, "u_actor", target(seq = 99))
        assertEquals(99L, cache.find("u_self", recall.copy(group = false, peerId = "u_actor", msgId = 1234))?.msgSeq)
        assertNull(cache.find("u_self", recall.copy(group = false, peerId = "u_actor", msgId = 5678)))
    }
}
