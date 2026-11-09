package com.owo233.tcqt.core.dexkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DexKitScanProgressTest {
    @Test
    fun slowFeaturesOnlyIncludesStrictlyMoreThanOneAndAHalfSecondsAndKeepsTopFive() {
        fun feature(millis: Long) = DexKitFeatureScanState("$millis", 1, 1, 0, millis)
        assertTrue(DexKitScanState(0, featureTimings = listOf(feature(1_499), feature(1_500))).slowFeatures.isEmpty())
        assertEquals(listOf(feature(1_501)), DexKitScanState(0, featureTimings = listOf(feature(1_501))).slowFeatures)
        val state = DexKitScanState(0, featureTimings = listOf(
            feature(1_501), feature(1_500), feature(7_000), feature(5_000),
            feature(6_000), feature(4_000), feature(8_000),
        ))
        assertEquals(listOf(8_000L, 7_000L, 6_000L, 5_000L, 4_000L), state.slowFeatures.map { it.elapsedMillis })
    }

    @Test
    fun featureIsPublishedBeforeLookupAndClocksSurviveFeatureSwitchAndSaving() {
        var nowMillis = 1_000L
        var state = DexKitScanState(total = 3)
        val progress = DexKitScanProgress(setOf("a", "b", "c"), clockMillis = { nowMillis }) { state = it }
        progress.start()
        nowMillis = 1_500
        progress.beginFeature("功能甲", setOf("a", "b"))
        assertEquals("功能甲", state.currentFeature?.name)
        assertEquals(2, state.currentFeature?.total)
        assertEquals(0, state.currentFeature?.completed)
        assertEquals(0, state.completed)
        assertEquals(DexKitScanPhase.SCANNING, state.phase)

        // A slow blocking query emits no events; the UI can still derive live elapsed time.
        nowMillis = 6_500
        assertEquals(5_500, state.elapsedMillisAt(nowMillis))
        assertEquals(1_500, state.currentFeature?.startedAtMillis)
        progress.record("a", true)
        assertEquals(5_000, state.currentFeature?.elapsedMillis)
        assertEquals(1, state.currentFeature?.completed)
        progress.record("b", true)
        progress.endFeature()
        assertNull(state.currentFeature)
        assertEquals(5_000, state.featureTimings.single().elapsedMillis)

        nowMillis = 7_000
        progress.beginFeature("功能乙", setOf("c"))
        assertEquals("功能乙", state.currentFeature?.name)
        assertEquals(0, state.currentFeature?.completed)
        assertEquals(0, state.currentFeature?.elapsedMillis)
        assertEquals(2, state.completed)
        nowMillis = 8_000
        progress.record("c", true)
        progress.savingResults()
        assertNull(state.currentFeature)
        assertEquals(DexKitScanPhase.SAVING, state.phase)
        assertEquals(listOf(5_000L, 1_000L), state.featureTimings.map { it.elapsedMillis })
        nowMillis = 9_000
        progress.finish(restartRequired = false)
        assertEquals(8_000, state.elapsedMillis)
        assertEquals(8_000, state.elapsedMillisAt(50_000))
    }

    @Test
    fun featureProgressExcludesCachedKeysReusesSharedSuccessAndRetriesSharedFailure() {
        var state = DexKitScanState(total = 3)
        val progress = DexKitScanProgress(setOf("sharedOk", "sharedRetry", "new")) { state = it }
        progress.beginFeature("功能甲", setOf("sharedOk", "sharedRetry"))
        progress.record("sharedOk", true)
        progress.record("sharedRetry", false)
        progress.endFeature()

        progress.beginFeature("功能乙", setOf("cached", "sharedOk", "sharedRetry", "new"))
        assertEquals(3, state.currentFeature?.total)
        assertEquals(1, state.currentFeature?.completed)
        progress.record("cached", true)
        assertEquals(1, state.currentFeature?.completed)
        progress.record("sharedRetry", true)
        progress.record("sharedRetry", true)
        assertEquals(2, state.currentFeature?.completed)
        assertEquals(2, state.completed)
        assertEquals(0, state.failed)
        progress.record("new", true)
        progress.endFeature()
        assertEquals(3, state.featureTimings.last().completed)
        assertEquals(3, state.completed)
    }

    @Test
    fun sharedFallbackStillShowsScanningEvenWhenOverallCountHasReachedTotal() {
        var state = DexKitScanState(total = 1)
        val progress = DexKitScanProgress(setOf("shared")) { state = it }
        progress.beginFeature("功能甲", setOf("shared"))
        progress.record("shared", false)
        progress.beginFeature("功能乙", setOf("shared"))
        assertEquals(state.total, state.completed)
        assertEquals(DexKitScanPhase.SCANNING, state.phase)
        assertEquals(0, state.currentFeature?.completed)
        progress.record("shared", true)
        progress.savingResults()
        assertNull(state.currentFeature)
        assertEquals(DexKitScanPhase.SAVING, state.phase)
    }

    @Test
    fun abortedFeatureKeepsItsMeasuredTimeAndFailureReporting() {
        var nowMillis = 0L
        var state = DexKitScanState(total = 2)
        val progress = DexKitScanProgress(setOf("ok", "skipped"), clockMillis = { nowMillis }) { state = it }
        progress.beginFeature("异常功能", setOf("ok", "skipped"))
        progress.record("ok", true)
        nowMillis = 2_500
        progress.finish(restartRequired = false, errorMessage = "扫描中断")
        assertNull(state.currentFeature)
        val feature = assertNotNull(state.featureTimings.singleOrNull())
        assertEquals(2_500, feature.elapsedMillis)
        assertEquals(1, feature.completed)
        assertEquals(1, state.failed)
        assertEquals(listOf("skipped"), state.failedKeys)
        assertEquals("扫描中断", state.errorMessage)
    }

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
        assertTrue(state.failedKeys.isEmpty())
        assertTrue(state.failedFeatureNames.isEmpty())
    }

    @Test
    fun sharedFailuresListEveryOwnerOnceAndRecoveredKeysAreRemoved() {
        var state = DexKitScanState(total = 2)
        val owners = mapOf(
            "shared" to listOf("功能甲", "功能乙"),
            "other" to listOf("功能乙", "功能丙"),
        )
        val progress = DexKitScanProgress(linkedSetOf("shared", "other"), owners) { state = it }
        progress.record("other", false)
        progress.record("shared", false)
        assertEquals(listOf("shared", "other"), state.failedKeys)
        assertEquals(listOf("功能甲", "功能乙", "功能丙"), state.failedFeatureNames)
        assertEquals(state.failed, state.failedKeys.size)

        progress.record("shared", true)
        progress.record("shared", false)
        assertEquals(listOf("other"), state.failedKeys)
        assertEquals(listOf("功能乙", "功能丙"), state.failedFeatureNames)

        progress.record("other", true)
        progress.finish(restartRequired = false)
        assertTrue(state.failedKeys.isEmpty())
        assertTrue(state.failedFeatureNames.isEmpty())
    }

    @Test
    fun unknownOwnerFallsBackToScanKeyWithoutIncludingUnscannedKeys() {
        var state = DexKitScanState(total = 1)
        val progress = DexKitScanProgress(setOf("unknown")) { state = it }
        progress.record("cached", false)
        assertTrue(state.failedKeys.isEmpty())
        progress.finish(restartRequired = false)
        assertEquals(listOf("unknown"), state.failedKeys)
        assertEquals(listOf("unknown"), state.failedFeatureNames)
    }

    @Test
    fun abortedTasksAndBridgeFailureStillProduceCompleteFailureCounts() {
        var state = DexKitScanState(total = 3)
        val owners = mapOf(
            "ok" to listOf("成功功能"),
            "throws" to listOf("异常功能"),
            "skipped" to listOf("未执行功能"),
        )
        val progress = DexKitScanProgress(setOf("ok", "throws", "skipped"), owners) { state = it }
        progress.record("ok", true)
        progress.finish(restartRequired = false, errorMessage = "保留原缓存")
        assertEquals(3, state.completed)
        assertEquals(1, state.succeeded)
        assertEquals(2, state.failed)
        assertEquals(listOf("throws", "skipped"), state.failedKeys)
        assertEquals(listOf("异常功能", "未执行功能"), state.failedFeatureNames)
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

    @Test
    fun changedHostCodeRequiresRestartEvenWhenDescriptorsStayTheSame() {
        var state = DexKitScanState(total = 1)
        val descriptors = mapOf("method" to "Lhost;->method()V")
        val progress = DexKitScanProgress(descriptors.keys) { state = it }
        progress.record("method", true)
        progress.finish(descriptors, descriptors, previousCacheUsable = false)
        assertTrue(state.restartRequired)
        assertFalse(state.canDismiss)

        progress.finish(descriptors, descriptors, previousCacheUsable = true)
        assertFalse(state.restartRequired)
        assertTrue(state.canDismiss)
    }
}
