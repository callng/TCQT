package com.owo233.tcqt.core.dexkit

import kotlin.test.*

class CachePolicyTest {
    @Test fun ordinaryCodeDoesNotRequireAPatchFingerprint() {
        assertTrue(DexKitHostCode(false).matches(null)); assertTrue(DexKitHostCode(false).matches("legacy"))
    }
    @Test fun unreadablePatchedCodeAlwaysRequiresRescan() {
        assertFalse(DexKitHostCode(true).matches(null)); assertFalse(DexKitHostCode(true, "").matches(""))
    }
    @Test fun patchFingerprintMustMatchTheLoadedCode() {
        assertTrue(DexKitHostCode(true, "loaded").matches("loaded"))
        assertFalse(DexKitHostCode(true, "loaded").matches("previous"))
    }
    @Test fun lookupCompletionReportsActualSuccess() {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val tracker = DexKitLookupTracker { name, succeeded -> calls += name to succeeded }
        tracker.markCompleted("missing"); tracker.markAllSucceeded(listOf("found")); tracker.markCompleted("found")
        assertEquals(listOf("missing" to false, "found" to true), calls)
    }
}
