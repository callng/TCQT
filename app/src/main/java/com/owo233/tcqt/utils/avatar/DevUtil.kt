/**
 * https://github.com/HdShare/NullAvatar
 */

package com.owo233.tcqt.utils.avatar

import com.owo233.tcqt.utils.log.Log

fun printStackTrace() {
    val stackTrace = Throwable().stackTrace
    val stackTraceStr = stackTrace.joinToString("\n") { element ->
        "at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
    }
    Log.d("StackTrace\n$stackTraceStr")
}
