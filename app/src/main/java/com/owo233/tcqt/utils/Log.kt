package com.owo233.tcqt.utils

import android.util.Log
import com.owo233.tcqt.data.TCQTBuild
import de.robv.android.xposed.XposedBridge

/**
 * 本页代码来源: https://github.com/xfqwdsj/IAmNotADeveloper
 */
private const val LogTag = TCQTBuild.HOOK_TAG

interface Logger {

    fun v(message: String, throwable: Throwable? = null) {
        Log.v(LogTag, message, throwable)
        FileLog.v(message, LogTag, throwable)
    }

    fun d(message: String, throwable: Throwable? = null) {
        Log.d(LogTag, message, throwable)
        FileLog.d(message, LogTag, throwable)
    }

    fun i(message: String, throwable: Throwable? = null) {
        Log.i(LogTag, message, throwable)
        FileLog.i(message, LogTag, throwable)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(LogTag, message, throwable)
        FileLog.w(message, LogTag, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(LogTag, message, throwable)
        FileLog.e(message, LogTag, throwable)
    }

    val debug get() = DebugLogger(this)
}

interface XposedLogger : Logger {

    override fun w(message: String, throwable: Throwable?) {
        bridgeLog("WARN", message, throwable)
        FileLog.w(message, tr = throwable)
    }

    override fun e(message: String, throwable: Throwable?) {
        bridgeLog("ERROR", message, throwable)
        FileLog.e(message, tr = throwable)
    }

    private fun bridgeLog(level: String, message: String, throwable: Throwable? = null) {
        XposedBridge.log("[$level] $LogTag: $message")
        if (throwable != null) {
            XposedBridge.log(throwable)
        }
    }
}

object Log : XposedLogger {

    object Android : Logger
}

class DebugLogger(private val delegate: Logger) : Logger by delegate {

    override fun v(message: String, throwable: Throwable?) {
        if (TCQTBuild.DEBUG) {
            delegate.v(message, throwable)
        }
    }

    override fun d(message: String, throwable: Throwable?) {
        if (TCQTBuild.DEBUG) {
            delegate.d(message, throwable)
        }
    }

    override fun i(message: String, throwable: Throwable?) {
        if (TCQTBuild.DEBUG) {
            delegate.i(message, throwable)
        }
    }

    override fun w(message: String, throwable: Throwable?) {
        if (TCQTBuild.DEBUG) {
            delegate.w(message, throwable)
        }
    }

    override fun e(message: String, throwable: Throwable?) {
        if (TCQTBuild.DEBUG) {
            delegate.e(message, throwable)
        }
    }
}
