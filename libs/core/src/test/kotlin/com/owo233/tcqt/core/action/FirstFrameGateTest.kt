package com.owo233.tcqt.core.action

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstFrameGateTest {
    @Test fun `timeout completes absent UI and ignores a late frame`() {
        val gate = FirstFrameGate()
        assertTrue(gate.complete(FirstFrameGate.Reason.TIMEOUT, 40))
        assertFalse(gate.complete(FirstFrameGate.Reason.FRAME_COMMIT, 50))
        assertEquals(FirstFrameGate.Completion(FirstFrameGate.Reason.TIMEOUT, 40), gate.result)
    }
    @Test fun `concurrent signals dispatch only once`() {
        val gate = FirstFrameGate()
        val start = CountDownLatch(1)
        val winners = java.util.concurrent.atomic.AtomicInteger()
        val threads = (0..39).map { n -> thread {
            start.await()
            if (gate.complete(FirstFrameGate.Reason.entries[n % 4], n.toLong())) winners.incrementAndGet()
        } }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, winners.get())
        assertTrue(gate.result != null)
    }
}
