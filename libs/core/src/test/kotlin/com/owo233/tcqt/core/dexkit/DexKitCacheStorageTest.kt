package com.owo233.tcqt.core.dexkit

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DexKitCacheStorageTest {
    private val normal = "DexKitCache_release_normal"

    private class Backend(val directory: File) {
        var opens = 0
        var closes = 0
        fun open(name: String): DexKitCacheBackend {
            opens++
            return object : DexKitCacheBackend {
                private val file = File(directory, "$name.kva")
                override fun read() = if (file.exists()) file.readLines().associate {
                    it.substringBefore('=') to it.substringAfter('=')
                } else emptyMap()
                override fun replace(values: Map<String, String>) {
                    file.writeText(values.entries.joinToString("\n") { "${it.key}=${it.value}" })
                }
                override fun close() { closes++ }
            }
        }
    }

    private fun withStore(block: (File, Backend, DexKitCacheStorage) -> Unit) {
        val directory = Files.createTempDirectory("tcqt-dexkit-storage").toFile()
        try {
            val backend = Backend(directory)
            block(directory, backend, DexKitCacheStorage(directory, backend::open))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingReadsAndClearNeverOpenOrCreateAnyCachePartition() = withStore { dir, backend, store ->
        assertTrue(store.read(normal).values.isEmpty())
        assertTrue(store.read("DexKitCache_release_hot_patch").values.isEmpty())
        store.clear()
        store.clear()
        assertEquals(0, backend.opens)
        assertEquals(listOf(".cache.lock"), dir.list()!!.toList())
    }

    @Test
    fun clearDeletesBothBuildTypesAndLegacyFilesWithoutTouchingOtherFiles() = withStore { dir, backend, store ->
        for (build in listOf("debug", "release")) {
            for (mode in listOf("", "_normal", "_hot_patch")) {
                for (suffix in listOf(".kva", ".kvb", ".kvc", ".tmp")) {
                    File(dir, "DexKitCache_$build$mode$suffix").writeText("cache")
                }
            }
        }
        File(dir, "unrelated.kva").writeText("keep")
        store.clear()
        assertEquals(0, backend.opens)
        assertEquals(setOf(".cache.lock", "unrelated.kva"), dir.list()!!.toSet())
        assertEquals("keep", File(dir, "unrelated.kva").readText())
    }

    @Test
    fun staleScanCannotRepopulateFilesAfterAnotherOwnerClearsThem() = withStore { dir, backend, first ->
        val second = DexKitCacheStorage(dir, backend::open)
        val scan = first.read(normal)
        assertTrue(first.write(normal, scan.generation, mapOf("key" to "old")))
        second.clear()
        val opens = backend.opens
        assertFalse(first.write(normal, scan.generation, mapOf("key" to "stale")))
        assertEquals(opens, backend.opens)
        assertFalse(File(dir, "$normal.kva").exists())
        val fresh = first.read(normal)
        assertTrue(fresh.generation > scan.generation)
        assertTrue(second.write(normal, fresh.generation, mapOf("key" to "new")))
        assertEquals(mapOf("key" to "new"), first.read(normal).values)
        assertEquals(backend.opens, backend.closes)
    }

    @Test
    fun partitionReadsAreFreshAndIsolated() = withStore { dir, backend, store ->
        val second = DexKitCacheStorage(dir, backend::open)
        val generation = store.read(normal).generation
        assertTrue(store.write(normal, generation, mapOf("key" to "normal")))
        assertTrue(second.write("DexKitCache_release_hot_patch", generation, mapOf("key" to "patch")))
        assertEquals("normal", second.read(normal).values["key"])
        assertEquals("patch", store.read("DexKitCache_release_hot_patch").values["key"])
        assertEquals(backend.opens, backend.closes)
    }

    @Test
    fun separateOwnersSerializeReadersWithSnapshotWrites() = withStore { dir, _, store ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val writer = DexKitCacheStorage(dir) { name ->
            object : DexKitCacheBackend {
                override fun read() = emptyMap<String, String>()
                override fun replace(values: Map<String, String>) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    File(dir, "$name.kva").writeText("key=complete")
                }
                override fun close() = Unit
            }
        }
        try {
            val generation = store.read(normal).generation
            val write = executor.submit<Boolean> { writer.write(normal, generation, mapOf("key" to "complete")) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val read = executor.submit<DexKitStoredCache> { store.read(normal) }
            assertFalse(read.isDone)
            release.countDown()
            assertTrue(write.get(5, TimeUnit.SECONDS))
            assertEquals(mapOf("key" to "complete"), read.get(5, TimeUnit.SECONDS).values)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
