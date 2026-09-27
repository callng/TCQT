package com.owo233.tcqt.core.log

import android.util.Log
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.hook.HookEngineManager

enum class LogLevel {

    VERBOSE, DEBUG, INFO, WARN, ERROR
}

private val XPOSED_OUTPUT_LEVELS =
    setOf(LogLevel.INFO, LogLevel.DEBUG, LogLevel.WARN, LogLevel.ERROR)

interface Logger {

    fun log(level: LogLevel, message: String, throwable: Throwable? = null)

    fun v(message: String, throwable: Throwable? = null) = log(LogLevel.VERBOSE, message, throwable)
    fun d(message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, message, throwable)
    fun i(message: String, throwable: Throwable? = null) = log(LogLevel.INFO, message, throwable)
    fun w(message: String, throwable: Throwable? = null) = log(LogLevel.WARN, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)
}

/**
 * Android 日志输出。
 *
 * 单元测试跑在 JVM 上，`android.util.Log` 是抛 `RuntimeException("not mocked")` 的空壳。
 * 日志失败**绝不能**把业务逻辑带崩（解析失败路径上打一行诊断就炸过一次），因此这里
 * 先探测一次可用性，不可用就整体跳过；探测结果缓存，正常设备上无额外开销。
 */
class AndroidLogger(private val tag: String) : Logger {

    private val available: Boolean by lazy {
        runCatching {
            Log.i(tag, "logger-probe")
            true
        }.getOrDefault(false)
    }

    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        if (!available) return

        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}

class XposedLogger(
    private val tag: String,
    private val androidLogger: Logger = AndroidLogger(tag)
) : Logger {

    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        androidLogger.log(level, message, throwable)

        val priority = when (level) {
            LogLevel.VERBOSE -> Log.VERBOSE
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }

        if (level in XPOSED_OUTPUT_LEVELS && HookEngineManager.isInitialized) {
            HookEngineManager.engine.log(priority, tag, message, throwable)
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
