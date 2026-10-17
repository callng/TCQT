package com.owo233.tcqt.core.dexkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DexKitScanProgressTest {
    @Test
    fun sharedKeysAreCountedOnceAndFallbackCanRecoverFailure() {
        var state = DexKitScanState(total = 2)
        val progress = DexKitScanProgress(setOf("shared", "other")) { state = it }
        val tracker = DexKitLookupTracker(progress::record)

        tracker.markCompleted("shared")
        tracker.markCompleted("shared")
        assertEquals(1, state.completed)
        assertEquals(1, state.failed)

        tracker.markSucceeded("shared")
        tracker.markCompleted("shared")
        progress.record("shared", false)
        assertEquals(1, state.completed)
        assertEquals(1, state.succeeded)
        assertEquals(0, state.failed)

        tracker.markSucceeded("other")
        tracker.markCompleted("other")
        progress.finish(restartRequired = true)
        assertEquals(2, state.total)
        assertEquals(2, state.completed)
        assertEquals(2, state.succeeded)
        assertEquals(0, state.failed)
        assertFalse(state.canDismiss)
    }

    @Test
    fun partialScanDoesNotCountReusedCachedKeys() {
        var state = DexKitScanState(total = 1)
        val progress = DexKitScanProgress(setOf("new")) { state = it }
        val tracker = DexKitLookupTracker(progress::record)
        tracker.markAllSucceeded(listOf("cached"))
        tracker.markCompleted("cached")
        assertEquals(0, state.completed)
        tracker.markSucceeded("new")
        tracker.markCompleted("new")
        progress.finish(restartRequired = true)
        assertEquals(1, state.total)
        assertEquals(1, state.succeeded)
    }

    @Test
    fun abortedTasksAndBridgeFailureStillProduceCompleteFailureCounts() {
        var state = DexKitScanState(total = 3)
        val progress = DexKitScanProgress(setOf("ok", "throws", "skipped")) { state = it }
        progress.record("ok", true)
        progress.finish(restartRequired = false, errorMessage = "保留原缓存")
        assertEquals(3, state.completed)
        assertEquals(1, state.succeeded)
        assertEquals(2, state.failed)
        assertEquals("保留原缓存", state.errorMessage)
        assertTrue(state.canDismiss)
    }

    @Test
    fun onlyFinishedUnchangedResultsCanBeDismissed() {
        assertFalse(DexKitScanState(total = 1).canDismiss)
        assertFalse(DexKitScanState(total = 1, completed = 1).canDismiss)
        assertFalse(DexKitScanState(total = 1, finished = true, restartRequired = true).canDismiss)
        assertTrue(DexKitScanState(total = 1, finished = true).canDismiss)
    }

    @Test
    fun emptyScanFinishesWithoutInvalidCounts() {
        var state = DexKitScanState(total = 0)
        val progress = DexKitScanProgress(emptySet()) { state = it }
        progress.finish(restartRequired = false)
        assertEquals(0, state.completed)
        assertEquals(0, state.failed)
        assertTrue(state.canDismiss)
    }

    @Test
    fun restartDependsOnExactSavedDescriptorsIncludingAddedRemovedAndEmptyKeys() {
        var state = DexKitScanState(total = 2)
        val previous = linkedMapOf("a" to "Lold;", "b" to "")
        fun finish(saved: Map<String, String>) {
            DexKitScanProgress(setOf("a", "b")) { state = it }.finish(previous, saved)
        }

        finish(linkedMapOf("b" to "", "a" to "Lold;"))
        assertTrue(state.canDismiss)
        assertFalse(state.restartRequired)

        finish(mapOf("a" to "Lnew;", "b" to ""))
        assertTrue(state.restartRequired)
        assertFalse(state.canDismiss)

        finish(previous + ("new" to ""))
        assertTrue(state.restartRequired)
        finish(previous - "b")
        assertTrue(state.restartRequired)

        DexKitScanProgress(emptySet()) { state = it }.finish(emptyMap(), emptyMap())
        assertTrue(state.canDismiss)
    }
}
