package com.owo233.tcqt.utils.log

import android.util.Log
import com.owo233.tcqt.data.TCQTBuild
import de.robv.android.xposed.XposedBridge

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

private val XPOSED_OUTPUT_LEVELS = setOf(LogLevel.DEBUG, LogLevel.WARN, LogLevel.ERROR)

interface Logger {
    fun log(level: LogLevel, message: String, throwable: Throwable? = null)

    fun v(message: String, throwable: Throwable? = null) = log(LogLevel.VERBOSE, message, throwable)
    fun d(message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, message, throwable)
    fun i(message: String, throwable: Throwable? = null) = log(LogLevel.INFO, message, throwable)
    fun w(message: String, throwable: Throwable? = null) = log(LogLevel.WARN, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)
}

class AndroidLogger(private val tag: String) : Logger {
    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}

class XposedLogger(private val tag: String) : Logger {

    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        val levelTag = when (level) {
            LogLevel.VERBOSE -> "VERBOSE"
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.INFO -> "INFO"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "ERROR"
        }

        if (level in XPOSED_OUTPUT_LEVELS) {
            XposedBridge.log("[$levelTag] $tag: $message")
            throwable?.let { XposedBridge.log(it) }
        }

        when (level) {
            LogLevel.VERBOSE -> FileLog.v(message, tag, throwable)
            LogLevel.DEBUG -> FileLog.d(message, tag, throwable)
            LogLevel.INFO -> FileLog.i(message, tag, throwable)
            LogLevel.WARN -> FileLog.w(message, tag, throwable)
            LogLevel.ERROR -> FileLog.e(message, tag, throwable)
        }
    }
}

class DebugFilterLogger(
    private val delegate: Logger,
    private val isDebug: Boolean = TCQTBuild.DEBUG
) : Logger by delegate {

    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        if (isDebug) {
            delegate.log(level, message, throwable)
        }
    }
}

object LogUtils {
    private const val TAG = TCQTBuild.HOOK_TAG

    val xposed: Logger = DebugFilterLogger(XposedLogger(TAG))

    val android: Logger = DebugFilterLogger(AndroidLogger(TAG))

    val xposedNoFilter: Logger = XposedLogger(TAG)

    val androidNoFilter: Logger = AndroidLogger(TAG)
}

object Log : Logger by LogUtils.xposed

object LogAndroid : Logger by LogUtils.android
