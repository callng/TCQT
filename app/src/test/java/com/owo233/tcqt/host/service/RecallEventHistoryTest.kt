package com.owo233.tcqt.host.service

import kotlinx.serialization.json.Json
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecallEventHistoryTest {
    private val key = RecallManager.MessageKey("u_self", 1, "u_peer", 3920)

    @Test
    fun refreshCannotLaunchSecondLookupWhileFirstGrayTipIsPending() {
        val history = RecallEventHistory<RecallManager.MessageKey>(2048)
        var pendingLookups = 0
        // This claim happens before the async query, not after its callback.
        if (history.add(key)) pendingLookups++
        repeat(20) { if (history.add(key)) pendingLookups++ }
        assertEquals(1, pendingLookups)
    }

    @Test
    fun rememberedRecallSuppressesReplayAfterSerializationAndRestart() {
        val before = RecallEventHistory<RecallManager.MessageKey>(2048)
        before.add(key)
        // Same JSON representation and identity used by RecallManager's existing FastKV data.
        val stored = Json.encodeToString(before.snapshot())
        val after = RecallEventHistory<RecallManager.MessageKey>(2048)
        after.restore(Json.decodeFromString<List<RecallManager.MessageKey>>(stored))
        assertFalse(after.add(key))
        assertTrue(after.containsAny(setOf(key)))
    }

    @Test
    fun simultaneousLiveAndSyncCallbacksClaimExactlyOnce() {
        val history = RecallEventHistory<RecallManager.MessageKey>(2048)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..40).map {
                pool.submit(Callable { start.await(); history.add(key) })
            }
            start.countDown()
            assertEquals(1, futures.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun distinctMessagesAccountsPeersAndChatTypesAreIndependent() {
        val history = RecallEventHistory<RecallManager.MessageKey>(2048)
        listOf(key, key.copy(msgSeq = 3921), key.copy(account = "u_other"),
            key.copy(peerId = "u_other"), key.copy(chatType = 2)
        ).forEach { assertTrue(history.add(it)) }
        assertEquals(5, history.snapshot().size)
    }

    @Test
    fun replayDoesNotRefreshAgeOrDisplaceOtherRetainedMarkers() {
        val history = RecallEventHistory<RecallManager.MessageKey>(2)
        val second = key.copy(msgSeq = 3921)
        val third = key.copy(msgSeq = 3922)
        history.add(key)
        history.add(second)
        assertFalse(history.add(key))
        history.add(third)
        assertEquals(listOf(second, third), history.snapshot())
        assertFalse(history.containsAny(setOf(key)))
    }
}
