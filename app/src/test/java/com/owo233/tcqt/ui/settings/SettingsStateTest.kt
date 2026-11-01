package com.owo233.tcqt.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsStateTest {
    @Test fun `reverting a value removes its draft`() {
        val session = SettingsEditSession()
        session.reload(mapOf("enabled" to false, "count" to 2, "name" to "old"))
        session.update("enabled", true)
        session.update("count", 3)
        assertEquals(2, session.changeCount)
        session.update("count", 2)
        assertEquals(1, session.changeCount)
        assertTrue(session.boolean("enabled"))
        assertFailsWith<IllegalArgumentException> { session.update("name", 1) }
    }
    @Test fun `failed save retains drafts and successful save commits once`() {
        val session = SettingsEditSession()
        session.reload(mapOf("a" to 1, "b" to "old"))
        session.update("a", 2)
        session.update("b", "new")
        assertFailsWith<IllegalStateException> { session.save { error("storage unavailable") } }
        assertEquals(2, session.changeCount)
        var commits = 0
        session.save { assertEquals(mapOf("a" to 2, "b" to "new"), it); commits++ }
        session.save { commits++ }
        assertEquals(1, commits)
        assertEquals(0, session.changeCount)
        assertEquals("new", session.string("b"))
    }
    @Test fun `restore invalidates stale drafts`() {
        val session = SettingsEditSession()
        session.reload(mapOf("key" to 1))
        session.update("key", 2)
        session.reload(mapOf("key" to 3))
        assertEquals(0, session.changeCount)
        assertEquals(3, session.int("key"))
    }
    @Test fun `search history trims deduplicates bounds and records only saved edits`() {
        var encoded = "[]"
        val state = SettingsSearchState({ encoded }, { encoded = it }, { throw it })
        state.load()
        state.enter()
        state.update(" draft ")
        state.rememberEdit()
        assertTrue(state.history.isEmpty())
        state.commitQueries()
        assertEquals(listOf("draft"), state.history.toList())
        repeat(20) { state.add("word$it") }
        state.add(" word19 ")
        assertEquals(10, state.history.size)
        assertEquals("word19", state.history.first())
        state.exit()
        assertEquals("", state.query)
        assertFalse(state.active)
    }
    @Test fun `search ranks exact then title then description with Unicode matching`() {
        val entries = listOf(
            FeatureSearch.Entry("desc", "other", "复读", 1),
            FeatureSearch.Entry("partial", "消息复读", "", 0),
            FeatureSearch.Entry("exact", "复读", "", 0),
            FeatureSearch.Entry("unmatched", "nothing", "", 0),
        )
        assertEquals(listOf("exact", "desc", "partial"), FeatureSearch.rank(entries, " 复读 "))
        assertTrue(FeatureSearch.rank(entries, " ").isEmpty())
    }
    @Test fun `picker cancellation and invalid keys never start an import`() = runBlocking {
        var applied = 0
        val importer = PngImportCoordinator<String>(this, { it == "icon" }, { it }, { _, _ -> applied++ }, {}, { throw it })
        assertFalse(importer.select("unknown"))
        assertTrue(importer.select("icon"))
        assertFalse(importer.select("icon"))
        importer.receive(null)
        assertEquals(0, applied)
        assertFalse(importer.isImporting)
    }
    @Test fun `cancelled import cannot overwrite a new selection`() = runBlocking(Dispatchers.Unconfined) {
        val first = CompletableDeferred<String>()
        val applied = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val importer = PngImportCoordinator<String>(this, { true }, { if (it == "first") first.await() else it },
            { _, value -> applied += value }, { busy += it }, { throw it })
        importer.select("icon"); importer.receive("first")
        assertTrue(importer.isImporting)
        importer.cancel()
        importer.select("icon"); importer.receive("second")
        first.complete("late")
        yield()
        assertEquals(listOf("second"), applied)
        assertFalse(importer.isImporting)
        assertEquals(false, busy.last())
    }
    @Test fun `failed image reads release import state and preserve the draft`() = runBlocking(Dispatchers.Unconfined) {
        var failures = 0
        var applied = false
        val importer = PngImportCoordinator<String>(this, { true }, { error("invalid PNG") },
            { _, _ -> applied = true }, {}, { failures++ })
        importer.select("icon"); importer.receive("bad")
        yield()
        assertEquals(1, failures)
        assertFalse(applied)
        assertFalse(importer.isImporting)
    }
}
