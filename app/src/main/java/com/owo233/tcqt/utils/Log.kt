package com.owo233.tcqt.utils

import com.owo233.tcqt.data.TCQTBuild
import de.robv.android.xposed.XposedBridge
import android.util.Log as ALog

object Log {

    private const val TAG = TCQTBuild.HOOK_TAG

    fun i(msg: String, e: Throwable? = null) = log(ALog.INFO, msg, e)
    fun w(msg: String, e: Throwable? = null) = log(ALog.WARN, msg, e)
    fun e(msg: String, e: Throwable? = null) = log(ALog.ERROR, msg, e)
    fun d(msg: String, e: Throwable? = null) = log(ALog.DEBUG, msg, e)

    private fun log(level: Int, msg: String, e: Throwable?) {
        val text = format(level, msg, e)

        if (level >= ALog.WARN || (level == ALog.DEBUG && TCQTBuild.DEBUG)) {
            XposedBridge.log(text)
        } else {
            ALog.println(level, TAG, text)
        }
    }

    private fun format(level: Int, msg: String, e: Throwable?) = buildString {
        append('[').append(TAG).append("][")

        append(
            when (level) {
                ALog.DEBUG -> "D"
                ALog.INFO  -> "I"
                ALog.WARN  -> "W"
                ALog.ERROR -> "E"
                else       -> "?"
            }
        )

        append("] ").append(msg).append('\n')

        e?.let {
            append(ALog.getStackTraceString(it))
            if (!endsWith('\n')) append('\n')
        }
    }
}
