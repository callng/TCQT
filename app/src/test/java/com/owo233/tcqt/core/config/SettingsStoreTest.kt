package com.owo233.tcqt.core.config

import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SettingsStoreTest {

    private class Backend(private val directory: File) {
        var opens = 0
        var failNextWrite = false
        fun open(): SettingsBackend {
            opens++
            return object : SettingsBackend {
                @Suppress("UNCHECKED_CAST")
                override fun read(): Map<String, Any> = File(directory, "data").let {
                    if (!it.exists()) emptyMap()
                    else ObjectInputStream(it.inputStream()).use { input -> input.readObject() as Map<String, Any> }
                }
                override fun replace(values: Map<String, Any>) {
                    if (failNextWrite) { failNextWrite = false; error("disk unavailable") }
                    val temp = File(directory, "data.tmp")
                    ObjectOutputStream(temp.outputStream()).use { it.writeObject(HashMap(values)) }
                    Files.move(temp.toPath(), File(directory, "data").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                override fun close() = Unit
            }
        }
    }

    private fun withStore(block: (File, Backend, SettingsStore) -> Unit) {
        val directory = Files.createTempDirectory("tcqt-settings").toFile()
        try {
            val backend = Backend(directory)
            block(directory, backend, SettingsStore(directory, "TCQT", backend::open))
        } finally { directory.deleteRecursively() }
    }

    @Test fun separateOwnersSeeChangesAndPreserveUnrelatedKeys() = withStore { dir, backend, first ->
        val second = SettingsStore(dir, "TCQT", backend::open)
        assertEquals(null, second["first"])
        first.put("first", true)
        second.put("second", "value")
        assertEquals(mapOf("first" to true, "second" to "value"), first.all())
        first.remove("first")
        assertFalse(second.contains("first"))
        assertEquals("value", second["second"])
        second.clear()
        assertEquals(emptyMap(), first.all())
    }

    @Test fun unchangedReadsReuseDecodedSnapshot() = withStore { _, backend, store ->
        store.put("value", 7)
        val opens = backend.opens
        repeat(100) { assertEquals(7, store["value"]) }
        assertEquals(opens, backend.opens)
    }

    @Test fun unchangedReadsDoNotAcquireFileLock() = withStore { dir, backend, store ->
        store.put("value", 7)
        val opens = backend.opens
        // The former implementation throws OverlappingFileLockException here.
        // A warmed Hook read must neither touch the file nor wait for a writer.
        RandomAccessFile(File(dir, ".TCQT.settings.lock"), "rw").use { file ->
            file.channel.lock().use {
                repeat(1_000) {
                    assertEquals(7, store["value"])
                    assertEquals(null, store["absent"])
                    assertFalse(store.contains("absent"))
                    assertEquals(mapOf("value" to 7), store.all())
                }
            }
        }
        assertEquals(opens, backend.opens)
    }

    @Test fun emptyLegacyLockFileIsInitializedWithoutLosingSettings() = withStore { dir, backend, _ ->
        backend.open().use { it.replace(mapOf("legacy" to 17)) }
        File(dir, ".TCQT.settings.lock").createNewFile()
        val reader = SettingsStore(dir, "TCQT", backend::open)
        assertEquals(17, reader["legacy"])
        assertEquals(8L, File(dir, ".TCQT.settings.lock").length())
        val writer = SettingsStore(dir, "TCQT", backend::open)
        writer.put("legacy", 18)
        assertEquals(18, reader["legacy"])
    }

    @Test fun failedCommitKeepsPreviousDataAndInvalidatesOtherOwners() = withStore { dir, backend, first ->
        val second = SettingsStore(dir, "TCQT", backend::open)
        first.put("value", "original")
        assertEquals("original", second["value"])
        backend.failNextWrite = true
        assertFailsWith<IllegalStateException> { first.replace(mapOf("value" to "replacement")) }
        assertEquals("original", first["value"])
        assertEquals("original", second["value"])
        second.put("new", true)
        assertEquals(mapOf("value" to "original", "new" to true), first.all())
    }

    @Test fun validationFailureNeverChangesPersistentData() = withStore { _, _, store ->
        store.put("value", 1)
        assertFailsWith<IllegalArgumentException> { store.update { it["value"] = 2; require(false) } }
        assertEquals(1, store["value"])
    }

    @Test fun concurrentOwnersDoNotLoseUpdates() = withStore { dir, backend, first ->
        first.put("count", 0)
        val pool = Executors.newFixedThreadPool(6)
        try {
            val futures = (0 until 6).map { writer -> pool.submit {
                val store = SettingsStore(dir, "TCQT", backend::open)
                repeat(30) { index -> store.update {
                    it["count"] = (it["count"] as Int) + 1
                    it["$writer.$index"] = true
                } }
            } }
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
            assertEquals(180, first["count"])
            assertEquals(181, first.all().size)
        } finally { pool.shutdownNow() }
    }
}
