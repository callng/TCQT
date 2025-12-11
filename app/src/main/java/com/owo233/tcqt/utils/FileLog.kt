package com.owo233.tcqt.utils

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.hooks.base.ProcUtil
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object FileLog {

    private const val LOG_MAX_SIZE = 2 * 1024 * 1024
    private const val LOG_KEEP_DAYS = 7

    private val mediaDir: File by lazy {
        @Suppress("DEPRECATION")
        return@lazy HookEnv.hostAppContext.externalMediaDirs
            ?.firstOrNull { it != null && it.canWrite() }
            ?.let { File(it, "${TCQTBuild.APP_NAME}/log") }
            ?.apply {
                if (isFile) delete()
                if (!exists()) mkdirs()
            }
            ?: throw IllegalStateException("No external storage available")
    }

    private val logFile: File
        get() {
            val file: File by lazy {
                return@lazy File(mediaDir, "log.txt")
                    .apply { if (!exists()) createNewFile() }
            }
            val f = file.apply {
                if (length() > LOG_MAX_SIZE) {
                    renameTo(File(mediaDir, "log_${LocalDateTime.now()}.txt"))
                    createNewFile()
                }
            }

            cleanOldLogs()
            return f
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
            val time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

            val thread = Thread.currentThread().name
            val procName = ProcUtil.currentProcName

            val logBuilder = StringBuilder()
                .append(time)
                .append(" ")
                .append("[$level]")
                .append("/")
                .append("[$tag]")
                .append(" [$procName]")
                .append("/")
                .append("[$thread]: ")
                .append(msg)
                .append("\n")

            tr?.let { logBuilder.append(it.stackTraceToString()).append("\n") }

            val bytes = logBuilder.toString().toByteArray()

            val fos = FileOutputStream(logFile, true)
            fos.channel.use { channel ->
                channel.lock().use {
                    fos.write(bytes)
                    fos.flush()
                }
            }
        } catch (_: Throwable) { }
    }

    private fun cleanOldLogs() {
        val deadlineMillis = System.currentTimeMillis() -
                LOG_KEEP_DAYS * 24L * 60L * 60L * 1000L

        mediaDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("log_") }
            ?.forEach { file ->
                val lastModified = file.lastModified()
                if (lastModified < deadlineMillis) {
                    file.delete()
                }
            }
    }
}
