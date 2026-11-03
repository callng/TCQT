package com.owo233.tcqt.features.debug

import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class Source32CaptureTest {
    @Test
    fun successfulSharedScanUnhooksOnceBeforeDeliveringTheResult() {
        val tasks = ArrayDeque<Runnable>()
        var unhooks = 0
        val capture = Source32Capture({ "ab".repeat(32) }, Executor(tasks::addLast))
        capture.onCaptured { unhooks++ }

        val signing = capture.begin()
        val manual = capture.begin()
        val delivered = manual.thenAccept { assertEquals(1, unhooks) }
        assertEquals(0, unhooks)
        assertSame(signing, manual)
        tasks.removeFirst().run()
        delivered.join()
        assertEquals("ab".repeat(32), manual.join())
        assertEquals("ab".repeat(32), capture.begin().join())
        assertEquals(1, unhooks)
    }

    @Test
    fun missesAndInvalidResultsKeepTheHookUntilSuccessfulCapture() {
        val results = ArrayDeque(listOf("", "g".repeat(64), "ab".repeat(32)))
        val capture = Source32Capture({ results.removeFirst() }, Executor(Runnable::run))
        var unhooks = 0
        capture.onCaptured { unhooks++ }

        assertNull(capture.begin().join())
        assertNull(capture.begin().join())
        assertEquals(0, unhooks)
        assertEquals("ab".repeat(32), capture.begin().join())
        assertEquals(1, unhooks)
    }

    @Test
    fun successfulScanBeforeHookHandleRegistrationStillUnhooksImmediately() {
        val capture = Source32Capture({ "ab".repeat(32) }, Executor(Runnable::run))
        assertEquals("ab".repeat(32), capture.begin().join())
        var unhooks = 0
        capture.onCaptured { unhooks++ }
        assertEquals(1, unhooks)
        assertEquals("ab".repeat(32), capture.begin().join())
        assertEquals(1, unhooks)
    }

    @Test
    fun nativeHookAndManualRequestShareOneScanAndReuseSuccessfulCapture() {
        val tasks = ArrayDeque<Runnable>()
        var scans = 0
        val source32 = "30b903bf096811eb400277afa05d0000000015850573459f994b45cd3d276d61"
        val capture = Source32Capture({ scans++; source32 }, Executor(tasks::addLast))

        val hook = capture.begin()
        val request = capture.begin()
        assertSame(hook, request)
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(source32, request.join())
        assertEquals(source32, capture.begin().join())
        assertEquals(1, scans)
        assertEquals(0, tasks.size)
    }

    @Test
    fun missesAndInvalidResultsDoNotPreventCaptureOnLaterSigningCalls() {
        val results = ArrayDeque(listOf("", "g".repeat(64), "ab".repeat(32)))
        val capture = Source32Capture({ results.removeFirst() }, Executor(Runnable::run))
        assertNull(capture.begin().join())
        assertNull(capture.begin().join())
        assertEquals("ab".repeat(32), capture.begin().join())
    }

    @Test
    fun scannerFailureCanRetryOnTheNextSigningCall() {
        var attempts = 0
        val capture = Source32Capture({
            if (attempts++ == 0) error("native scanner unavailable")
            "ab".repeat(32)
        }, Executor(Runnable::run))
        assertNull(capture.begin().join())
        assertEquals("ab".repeat(32), capture.begin().join())
    }
}
