package com.owo233.tcqt.utils.log

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Keeps the latest failure for each feature so the settings UI can show an
 * actionable error instead of requiring the user to search the global log.
 *
 * Records live in the host application's private files directory. This makes
 * them visible to all QQ/TIM processes without introducing another IPC layer.
 */
internal object ActionErrorStore {

    private const val DIRECTORY_NAME = "tcqt_action_errors"
    private const val MAX_DETAILS_LENGTH = 32 * 1024
    private const val CURRENT_SCHEMA_VERSION = 2

    private val json = Json { ignoreUnknownKeys = true }
    private val currentActionKey = ThreadLocal<String?>()

    @Serializable
    data class Record(
        val actionKey: String,
        val occurredAt: Long,
        val processName: String,
        val stage: String,
        val summary: String,
        val details: String,
        val moduleVersionCode: Long,
        val schemaVersion: Int = 1
    )

    fun currentActionKey(): String? = currentActionKey.get()

    fun <T> withAction(actionKey: String, block: () -> T): T {
        val previous = currentActionKey.get()
        currentActionKey.set(actionKey)
        return try {
            block()
        } finally {
            if (previous == null) currentActionKey.remove() else currentActionKey.set(previous)
        }
    }

    fun reportCurrent(stage: String, throwable: Throwable) {
        currentActionKey()?.let { report(it, stage, throwable) }
    }

    fun report(actionKey: String, stage: String, throwable: Throwable) {
        if (actionKey.isBlank()) return
        runCatching {
            val record = Record(
                actionKey = actionKey,
                occurredAt = System.currentTimeMillis(),
                processName = HookEnv.processName,
                stage = stage,
                summary = buildSummary(throwable),
                details = throwable.stackTraceToString().take(MAX_DETAILS_LENGTH),
                moduleVersionCode = TCQTBuild.VER_CODE.toLong(),
                schemaVersion = CURRENT_SCHEMA_VERSION
            )
            writeRecord(record)
        }
    }

    fun readAll(): Map<String, Record> {
        val directory = errorDirectory() ?: return emptyMap()
        return directory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file -> readRecord(file) }
            .filter {
                it.schemaVersion == CURRENT_SCHEMA_VERSION &&
                        it.moduleVersionCode == TCQTBuild.VER_CODE.toLong()
            }
            .groupBy { it.actionKey }
            .mapValues { (_, records) -> records.maxBy { it.occurredAt } }
    }

    fun clear(actionKey: String) {
        clear(actionKey, processName = null)
    }

    /** Clears only one host process, or every process when [processName] is null. */
    fun clear(actionKey: String, processName: String?) {
        if (actionKey.isBlank()) return
        runCatching {
            val directory = errorDirectory() ?: return
            val encodedActionKey = encode(actionKey)
            if (processName != null) {
                File(directory, "${encodedActionKey}.${encode(processName)}.json").delete()
                return@runCatching
            }
            directory.listFiles { file -> file.isFile && file.extension == "json" }
                .orEmpty()
                .filter { file ->
                    file.name == "$encodedActionKey.json" || readRecord(file)?.actionKey == actionKey
                }
                .forEach { it.delete() }
        }
    }

    private fun writeRecord(record: Record) {
        val file = recordFile(record.actionKey, record.processName) ?: return
        val bytes = json.encodeToString(Record.serializer(), record)
            .toByteArray(StandardCharsets.UTF_8)
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.lock().use {
                raf.setLength(0)
                raf.write(bytes)
                raf.fd.sync()
            }
        }
    }

    private fun readRecord(file: File): Record? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.channel.lock(0L, Long.MAX_VALUE, true).use {
                val bytes = ByteArray(raf.length().toInt())
                raf.readFully(bytes)
                json.decodeFromString(Record.serializer(), String(bytes, StandardCharsets.UTF_8))
            }
        }
    }.getOrNull()

    private fun recordFile(actionKey: String, processName: String): File? {
        return errorDirectory()?.let { File(it, "${encode(actionKey)}.${encode(processName)}.json") }
    }

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun errorDirectory(): File? = runCatching {
        File(HookEnv.hostAppContext.filesDir, DIRECTORY_NAME).apply {
            if (!exists() && !mkdirs()) return null
        }
    }.getOrNull()

    private fun buildSummary(throwable: Throwable): String {
        val type = throwable::class.java.simpleName.ifBlank { "Throwable" }
        val message = throwable.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return if (message.isBlank()) type else "$type: $message"
    }
}
