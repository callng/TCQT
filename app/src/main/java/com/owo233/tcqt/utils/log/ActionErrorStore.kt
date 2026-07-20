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
        val moduleVersionCode: Long
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
                moduleVersionCode = TCQTBuild.VER_CODE.toLong()
            )
            writeRecord(record)
        }
    }

    fun readAll(): Map<String, Record> {
        val directory = errorDirectory() ?: return emptyMap()
        return directory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file -> readRecord(file) }
            .filter { it.moduleVersionCode == TCQTBuild.VER_CODE.toLong() }
            .associateBy { it.actionKey }
    }

    fun clear(actionKey: String) {
        runCatching { recordFile(actionKey)?.delete() }
    }

    private fun writeRecord(record: Record) {
        val file = recordFile(record.actionKey) ?: return
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

    private fun recordFile(actionKey: String): File? {
        val encodedKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(actionKey.toByteArray(StandardCharsets.UTF_8))
        return errorDirectory()?.let { File(it, "$encodedKey.json") }
    }

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
