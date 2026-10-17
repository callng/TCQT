package com.owo233.tcqt.core.dexkit

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/** Short-lived handles only: FastKV instances cache data and are not a multi-process store. */
internal interface DexKitCacheBackend : AutoCloseable {
    fun read(): Map<String, String>
    fun replace(values: Map<String, String>)
}

internal data class DexKitStoredCache(val generation: Long, val values: Map<String, String>)

/** Serializes complete snapshots across processes and invalidates scans started before a clear. */
internal class DexKitCacheStorage(
    private val directory: File,
    private val open: (String) -> DexKitCacheBackend,
) {
    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
        private val suffixes = listOf(".kva", ".kvb", ".kvc", ".tmp")
        private val names = listOf("debug", "release").flatMap { build ->
            listOf("DexKitCache_$build", "DexKitCache_${build}_normal", "DexKitCache_${build}_hot_patch")
        }
    }

    fun read(name: String): DexKitStoredCache = locked { file ->
        require(name in names)
        // Opening a missing FastKV store creates its A/B files, even for a read.
        val values = if (exists(name)) open(name).use { it.read() } else emptyMap()
        DexKitStoredCache(generation(file), values)
    }

    fun write(name: String, expectedGeneration: Long, values: Map<String, String>): Boolean = locked { file ->
        require(name in names)
        if (generation(file) != expectedGeneration) return@locked false
        open(name).use { it.replace(values) }
        true
    }

    fun clear() = locked { file ->
        // Write and sync the generation BEFORE deletion, including when deletion fails part-way.
        val next = generation(file) + 1
        file.seek(0)
        file.writeLong(next)
        file.fd.sync()
        names.forEach { name ->
            suffixes.forEach { suffix ->
                val target = File(directory, name + suffix)
                check(!target.exists() || target.delete()) { "Failed to remove ${target.name}" }
            }
        }
        next
    }

    private fun exists(name: String) = suffixes.any { File(directory, name + it).isFile }

    private fun generation(file: RandomAccessFile): Long {
        if (file.length() < Long.SIZE_BYTES) return 0
        file.seek(0)
        return file.readLong()
    }

    private fun <T> locked(block: (RandomAccessFile) -> T): T {
        // FileChannel locks do not wait for overlapping locks in the same VM.
        val processLock = locks.getOrPut(directory.canonicalPath) { Any() }
        return synchronized(processLock) {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create DexKit cache directory" }
            RandomAccessFile(File(directory, ".cache.lock"), "rw").use { file ->
                file.channel.lock().use { block(file) }
            }
        }
    }
}
