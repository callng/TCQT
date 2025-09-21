package com.owo233.tcqt.utils

import com.owo233.tcqt.data.TCQTBuild
import de.robv.android.xposed.XposedBridge

internal object Log {

    private const val TAG = "TCQT"

    fun i(msg: String, e: Throwable? = null) = log(android.util.Log.INFO, msg, e)

    fun w(msg: String, e: Throwable? = null) = log(android.util.Log.WARN, msg, e)

    fun e(msg: String, e: Throwable? = null) = log(android.util.Log.ERROR, msg, e)

    fun d(msg: String, e: Throwable? = null) = log(android.util.Log.DEBUG, msg, e)

    private fun log(level: Int, msg: String, e: Throwable? = null) {
        if (level <= android.util.Log.DEBUG && !TCQTBuild.DEBUG) return
        val logStr = parseLog(level, msg, e)
        XposedBridge.log(logStr)
    }

    private fun parseLog(level: Int, msg: String, e: Throwable? = null) = buildString {
        val levelStr = when (level) {
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            else -> "?"
        }

        append("[${TAG}][$levelStr] $msg")
        if (!endsWith('\n')) append('\n')
        if (e != null) append(android.util.Log.getStackTraceString(e))
        if (!endsWith('\n')) append('\n')
    }
}
