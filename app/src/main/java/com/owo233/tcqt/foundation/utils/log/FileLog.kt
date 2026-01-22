package com.owo233.tcqt.foundation.utils.log

import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.foundation.data.TCQTBuild
import com.owo233.tcqt.features.hooks.base.ProcUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object FileLog {

    private const val LOG_MAX_SIZE = 2 * 1024 * 1024
    private const val LOG_KEEP_DAYS = 7

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val mediaDir: File by lazy {
        @Suppress("DEPRECATION")
        HookEnv.hostAppContext.externalMediaDirs
            ?.firstOrNull { it != null && it.canWrite() }
            ?.let { File(it, "${TCQTBuild.APP_NAME}/log") }
            ?.apply { if (!exists()) mkdirs() }
            ?: throw IllegalStateException("No external storage available")
    }

    private val logFile: File
        get() {
            val baseFile = File(mediaDir, "log.txt")
            if (!baseFile.exists()) {
                baseFile.createNewFile()
            }

            if (baseFile.length() > LOG_MAX_SIZE) {
                rotateLogFile(baseFile)
            }

            return baseFile
        }

    private fun rotateLogFile(currentFile: File) {
        val timestamp = LocalDateTime.now().format(fileNameFormatter)
        val backupFile = File(mediaDir, "log_$timestamp.txt")

        if (currentFile.renameTo(backupFile)) {
            File(mediaDir, "log.txt").createNewFile()
            cleanOldLogs()
        }
    }

    fun i(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        writeLog("INFO", tag, msg, tr)

    fun d(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        writeLog("DEBUG", tag, msg, tr)

    fun w(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        writeLog("WARN", tag, msg, tr)

    fun e(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        writeLog("ERROR", tag, msg, tr)

    fun v(msg: String, tag: String = TCQTBuild.HOOK_TAG, tr: Throwable? = null) =
        writeLog("VERBOSE", tag, msg, tr)

    private fun writeLog(level: String, tag: String, msg: String, tr: Throwable? = null) {
        try {
            val time = LocalDateTime.now().format(logTimeFormatter)
            val thread = Thread.currentThread().name
            val procName = ProcUtil.currentProcName

            val logContent = buildString {
                append(time).append(" ")
                append("[$level]/[$tag] ")
                append("[$procName]/[$thread]: ")
                append(msg)
                append("\n")
                tr?.let { append(it.stackTraceToString()).append("\n") }
            }

            synchronized(this) {
                val targetFile = logFile
                FileOutputStream(targetFile, true).use { fos ->
                    fos.channel.use { channel ->
                        var lock: FileLock? = null
                        try {
                            lock = channel.lock()
                            fos.write(logContent.toByteArray())
                            fos.flush()
                        } finally {
                            lock?.release()
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // 吞掉异常
        }
    }

    private fun cleanOldLogs() {
        Thread {
            try {
                val deadline = System.currentTimeMillis() - LOG_KEEP_DAYS * 24L * 3600L * 1000L
                mediaDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("log_") }
                    ?.forEach { if (it.lastModified() < deadline) it.delete() }
            } catch (_: Throwable) {}
        }.start()
    }
}
