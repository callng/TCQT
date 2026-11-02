package com.owo233.tcqt.core.config

import io.fastkv.FastKV
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

internal interface SettingsBackend : AutoCloseable {
    fun read(): Map<String, Any>
    fun replace(values: Map<String, Any>)
}

/**
 * Cross-process snapshots over the existing FastKV format. Only this owner may open
 * a settings file: FastKV 3 caches per-process data and does not provide IPC locking.
 * The stable lock file's generation is mapped once. Unchanged reads only inspect
 * memory; opening files and acquiring the IPC lock is reserved for reloads/writes.
 */
internal class SettingsStore(
    private val directory: File,
    private val name: String,
    private val open: () -> SettingsBackend = { fastKvBackend(directory, name) },
) {
    companion object {
        private val processLocks = ConcurrentHashMap<String, Any>()

        private fun fastKvBackend(directory: File, name: String): SettingsBackend {
            val kv = FastKV.Builder(directory.absolutePath, name).blocking().build()
            return object : SettingsBackend {
                override fun read(): Map<String, Any> = kv.all

                override fun replace(values: Map<String, Any>) {
                    kv.disableAutoCommit()
                    // clear() deletes the committed C file in blocking mode. Removing
                    // keys in memory preserves the previous snapshot until commit.
                    kv.all.keys.filterNot(values::containsKey).forEach(kv::remove)
                    values.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> kv.putBoolean(key, value)
                            is Int -> kv.putInt(key, value)
                            is Long -> kv.putLong(key, value)
                            is Float -> kv.putFloat(key, value)
                            is Double -> kv.putDouble(key, value)
                            is String -> kv.putString(key, value)
                            is ByteArray -> kv.putArray(key, value)
                            is Set<*> -> {
                                require(value.all { it is String }) { "Unsupported setting: $key" }
                                @Suppress("UNCHECKED_CAST")
                                kv.putStringSet(key, value as Set<String>)
                            }
                            else -> error("Unsupported setting: $key (${value.javaClass.name})")
                        }
                    }
                    check(kv.commit()) { "Settings commit failed" }
                }

                override fun close() = kv.close()
            }
        }
    }

    private data class Snapshot(val generation: Long, val values: Map<String, Any>)

    private val lockPath by lazy {
        require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid settings file name" }
        File(directory, ".$name.settings.lock").canonicalPath
    }
    private val processLock by lazy { processLocks.getOrPut(lockPath) { Any() } }

    // Publish the generation and its immutable values together. The mapping is
    // initialized under the IPC lock before publishing the first snapshot.
    @Volatile private var cachedSnapshot: Snapshot? = null
    private var generationView: MappedByteBuffer? = null

    fun all(): Map<String, Any> = snapshot().values.toMap()
    operator fun get(key: String): Any? = snapshot().values[key]
    fun contains(key: String): Boolean = snapshot().values.containsKey(key)
    fun put(key: String, value: Any) = update { it[key] = value }
    fun remove(key: String) = update { it.remove(key) }
    fun clear() = replace(emptyMap())
    fun replace(values: Map<String, Any>) = update { it.clear(); it.putAll(values) }

    fun update(change: (MutableMap<String, Any>) -> Unit) = locked { file ->
        val previous = snapshot(file).values
        val next = previous.toMutableMap().also(change).toMap()
        if (next == previous) return@locked
        val generation = requireNotNull(cachedSnapshot).generation + 1L
        // Invalidate before committing: a writer killed after publishing data must
        // never leave another process reusing the previous in-memory snapshot.
        cachedSnapshot = null
        file.seek(0)
        file.writeLong(generation)
        file.fd.sync()
        open().use { it.replace(next) }
        cachedSnapshot = Snapshot(generation, next)
    }

    private fun snapshot(): Snapshot {
        val cached = cachedSnapshot
        if (cached != null && generationView?.getLong(0) == cached.generation) return cached
        // The writer publishes its generation before replacing FastKV data. A
        // mismatch must acquire the lock, so readers cannot cache a partial commit.
        return locked(::snapshot)
    }

    private fun snapshot(file: RandomAccessFile): Snapshot {
        if (generationView == null) {
            if (file.length() < Long.SIZE_BYTES) file.setLength(Long.SIZE_BYTES.toLong())
            generationView = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, Long.SIZE_BYTES.toLong())
        }
        file.seek(0)
        val generation = file.readLong()
        val cached = cachedSnapshot
        if (cached != null && cached.generation == generation) return cached
        return Snapshot(generation, open().use { it.read().toMap() }).also {
            cachedSnapshot = it
        }
    }

    private fun <T> locked(block: (RandomAccessFile) -> T): T {
        return synchronized(processLock) {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create settings directory" }
            RandomAccessFile(lockPath, "rw").use { file ->
                file.channel.lock().use { block(file) }
            }
        }
    }
}
