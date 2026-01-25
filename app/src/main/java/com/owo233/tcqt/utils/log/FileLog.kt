package com.owo233.tcqt.utils.log

import android.util.Log
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.hooks.base.ProcUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object FileLog {
    private const val LOG_MAX_SIZE = 2 * 1024 * 1024 // 2MB
    private const val LOG_KEEP_DAYS = 7
    private const val BACKUP_FILE_PREFIX = "log_"
    private const val DEFAULT_LOG_FILE_NAME = "log.txt"

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val logExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLog-Writer").apply { isDaemon = true }
    }

    private val mediaDir: File by lazy {
        @Suppress("DEPRECATION")
        HookEnv.hostAppContext.externalMediaDirs
            ?.firstOrNull { it != null && it.canWrite() }
            ?.let { File(it, "${TCQTBuild.APP_NAME}/log") }
            ?.apply { if (!exists()) mkdirs() }
            ?: throw IllegalStateException("No external storage available")
    }

    fun i(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        submitLog("INFO", tag, msg, tr)

    fun d(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        submitLog("DEBUG", tag, msg, tr)

    fun w(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        submitLog("WARN", tag, msg, tr)

    fun e(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        submitLog("ERROR", tag, msg, tr)

    fun v(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        submitLog("VERBOSE", tag, msg, tr)

    private fun submitLog(level: String, tag: String, msg: String, tr: Throwable?) {
        logExecutor.submit {
            try {
                writeLogToFile(level, tag, msg, tr)
            } catch (e: Exception) {
                Log.e(TCQTBuild.HOOK_TAG, "Failed to write log to file", e)
            }
        }
    }

    private fun writeLogToFile(level: String, tag: String, msg: String, tr: Throwable?) {
        val targetFile = getValidLogFile() ?: return
        val logContent = buildLogContent(level, tag, msg, tr)

        synchronized(FileLog::class.java) {
            var fos: FileOutputStream? = null
            var lock: FileLock? = null
            try {
                fos = FileOutputStream(targetFile, true)
                lock = fos.channel.lock()
                fos.write(logContent.toByteArray(StandardCharsets.UTF_8))
                fos.flush()
            } catch (e: IOException) {
                Log.e(TCQTBuild.HOOK_TAG, "IO error when writing log", e)
            } finally {
                try {
                    lock?.release()
                } catch (_: IOException) {}
                try {
                    fos?.close()
                } catch (_: IOException) {}
            }
        }
    }

    private fun buildLogContent(level: String, tag: String, msg: String, tr: Throwable?): String {
        return buildString {
            append(LocalDateTime.now().format(logTimeFormatter)).append(" ")
            append("[$level]/[$tag] ")
            append("[${ProcUtil.currentProcName}]/[${Thread.currentThread().name}]: ")
            append(msg)
            append("\n")
            tr?.let {
                append(it.stackTraceToString())
                append("\n")
            }
        }
    }

    private fun getValidLogFile(): File? {
        val baseDir = mediaDir
        val baseFile = File(baseDir, DEFAULT_LOG_FILE_NAME)

        try {
            if (!baseFile.exists()) {
                baseFile.createNewFile()
                if (!baseFile.canWrite()) {
                    Log.e(TCQTBuild.HOOK_TAG, "Log file is not writable: ${baseFile.absolutePath}")
                    return null
                }
            }

            if (baseFile.length() > LOG_MAX_SIZE) {
                rotateLogFile(baseFile)
                if (!baseFile.exists()) {
                    baseFile.createNewFile()
                }
            }

            return baseFile
        } catch (e: Exception) {
            Log.e(TCQTBuild.HOOK_TAG, "Failed to get valid log file", e)
            return null
        }
    }

    private fun rotateLogFile(currentFile: File) {
        try {
            val timestamp = LocalDateTime.now().format(fileNameFormatter)
            val backupFile = File(currentFile.parentFile, "$BACKUP_FILE_PREFIX$timestamp.txt")

            if (backupFile.exists()) {
                backupFile.delete()
            }

            if (currentFile.renameTo(backupFile)) {
                Log.d(TCQTBuild.HOOK_TAG, "Log file rotated to: ${backupFile.absolutePath}")
                cleanOldLogs()
            } else {
                Log.e(TCQTBuild.HOOK_TAG, "Failed to rotate log file: ${currentFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TCQTBuild.HOOK_TAG, "Error rotating log file", e)
        }
    }

    private fun cleanOldLogs() {
        logExecutor.submit {
            try {
                val baseDir = mediaDir
                val deadline = System.currentTimeMillis() - LOG_KEEP_DAYS * 24L * 3600L * 1000L

                baseDir.listFiles()?.filter { file ->
                    file.isFile && file.name.startsWith(BACKUP_FILE_PREFIX)
                }?.forEach { file ->
                    try {
                        if (file.lastModified() < deadline) {
                            if (file.delete()) {
                                Log.d(TCQTBuild.HOOK_TAG, "Deleted old log file: ${file.name}")
                            } else {
                                Log.w(TCQTBuild.HOOK_TAG, "Failed to delete old log file: ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TCQTBuild.HOOK_TAG, "Error deleting old log file: ${file.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TCQTBuild.HOOK_TAG, "Error cleaning old logs", e)
            }
        }
    }

    fun shutdown() {
        logExecutor.shutdown()
        try {
            if (!logExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            logExecutor.shutdownNow()
        }
    }
}
