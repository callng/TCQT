package com.owo233.tcqt.core.config

import io.fastkv.FastKV
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

internal interface SettingsBackend : AutoCloseable {
    fun read(): Map<String, Any>
    fun replace(values: Map<String, Any>)
}

/**
 * Cross-process snapshots over the existing FastKV format. Only this owner may open
 * a settings file: FastKV 3 caches per-process data and does not provide IPC locking.
 * Unchanged reads reuse the decoded snapshot; a committed generation invalidates it.
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

    private var cachedGeneration: Long? = null
    private var cachedValues: Map<String, Any> = emptyMap()

    fun all(): Map<String, Any> = locked { file -> snapshot(file).toMap() }
    operator fun get(key: String): Any? = locked { file -> snapshot(file)[key] }
    fun contains(key: String): Boolean = locked { file -> snapshot(file).containsKey(key) }
    fun put(key: String, value: Any) = update { it[key] = value }
    fun remove(key: String) = update { it.remove(key) }
    fun clear() = replace(emptyMap())
    fun replace(values: Map<String, Any>) = update { it.clear(); it.putAll(values) }

    fun update(change: (MutableMap<String, Any>) -> Unit) = locked { file ->
        val previous = snapshot(file)
        val next = previous.toMutableMap().also(change).toMap()
        if (next == previous) return@locked
        val generation = (cachedGeneration ?: 0L) + 1L
        // Invalidate before committing: a writer killed after publishing data must
        // never leave another process reusing the previous in-memory snapshot.
        cachedGeneration = null
        file.seek(0)
        file.writeLong(generation)
        file.fd.sync()
        open().use { it.replace(next) }
        cachedValues = next
        cachedGeneration = generation
    }

    private fun snapshot(file: RandomAccessFile): Map<String, Any> {
        file.seek(0)
        val generation = if (file.length() >= Long.SIZE_BYTES) file.readLong() else 0L
        if (cachedGeneration != generation) {
            cachedValues = open().use { it.read().toMap() }
            cachedGeneration = generation
        }
        return cachedValues
    }

    private fun <T> locked(block: (RandomAccessFile) -> T): T {
        require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid settings file name" }
        val lockPath = File(directory, ".$name.settings.lock").canonicalPath
        return synchronized(processLocks.getOrPut(lockPath) { Any() }) {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create settings directory" }
            RandomAccessFile(lockPath, "rw").use { file ->
                file.channel.lock().use { block(file) }
            }
        }
    }
}
