package com.owo233.tcqt.core.action

import kotlin.test.*

class StartupAfterCreateTest {
    @Test fun waitsForTheOriginalApplicationAndRunsOnce() {
        val startup = StartupAfterCreate<Any>()
        val app = Any()
        var calls = 0
        startup.prepare(app) { calls++ }
        startup.complete(Any(), null); assertEquals(0, calls)
        startup.complete(app, null); startup.complete(app, null); assertEquals(1, calls)
    }
    @Test fun failedOnCreateDiscardsThePlan() {
        val startup = StartupAfterCreate<Any>()
        val app = Any()
        var calls = 0
        startup.prepare(app) { calls++ }
        startup.complete(app, IllegalStateException()); startup.complete(app, null)
        assertEquals(0, calls)
    }
    @Test fun cannotOverwriteAnUnfinishedPlan() {
        val startup = StartupAfterCreate<Any>()
        startup.prepare(Any()) { }
        assertFailsWith<IllegalStateException> { startup.prepare(Any()) { } }
    }
}
