package com.owo233.tcqt.utils

internal object StackUtil {

    /** 默认最多读取 20 帧 */
    const val DEFAULT_DEPTH = 20
    /** 默认从 0 帧开始 */
    const val DEFAULT_START = 0

    private val SYSTEM_STACK_PREFIXES = setOf(
        "android.",
        "java.",
        "dalvik.",
        "javax.",
        "junit.",
        "org.apache.http.",
        "org.json.",
        "org.w3c.",
        "org.xml.",
        "org.xmlpull.",
        "UnityEngine.",
        "com.unity3d.",
        "androidx.",
        "kotlin.",
        "com.android",
        "libcore."
    )

    /**
     * 从 Throwable 提取简化的堆栈信息，过滤系统帧。
     */
    @JvmStatic
    fun getSimpleStacktrace(
        throwable: Throwable?,
        startIndex: Int = DEFAULT_START,
        maxDepth: Int = DEFAULT_DEPTH
    ): String {
        if (throwable == null) return ""
        val frames = throwable.stackTrace
        val lines = Array(frames.size) { idx ->
            if (idx in startIndex until startIndex + maxDepth) {
                frames[idx].toString()
            } else ""
        }
        return buildSimpleStacktrace(lines, startIndex, maxDepth)
    }

    /**
     * 从原始堆栈字符串提取简化堆栈信息。
     */
    @JvmStatic
    fun getSimpleStacktrace(
        rawStacktrace: String?,
        startIndex: Int = DEFAULT_START,
        maxDepth: Int = DEFAULT_DEPTH
    ): String {
        if (rawStacktrace == null) return ""
        return buildSimpleStacktrace(
            rawStacktrace.split("\n").toTypedArray(),
            startIndex,
            maxDepth
        )
    }

    /**
     * 从线程的 StackTraceElement 数组提取堆栈信息（不过滤系统帧）。
     */
    @JvmStatic
    fun getThreadStackTrace(
        stackFrames: Array<StackTraceElement>?,
        startIndex: Int = DEFAULT_START,
        maxDepth: Int = DEFAULT_DEPTH
    ): String {
        if (stackFrames == null) return ""
        val sb = StringBuilder()
        val endIndex = minOf(stackFrames.size, startIndex + maxDepth)
        for (i in startIndex until endIndex) {
            sb.appendLine(stackFrames[i])
        }
        return sb.toString()
    }

    private fun buildSimpleStacktrace(
        lines: Array<String>,
        startIndex: Int,
        maxDepth: Int
    ): String {
        val sb = StringBuilder()
        val endIndex = minOf(lines.size, startIndex + maxDepth)
        for (i in startIndex until endIndex) {
            val stripped = stripAtPrefix(lines[i])
            if (!isSystemStack(stripped)) {
                sb.appendLine(stripLineNumber(stripped))
            }
        }
        return sb.toString()
    }

    private fun isSystemStack(frame: String): Boolean =
        SYSTEM_STACK_PREFIXES.any { frame.startsWith(it) }

    /** 移除行首 "xxx.at " 前缀 */
    private fun stripAtPrefix(line: String): String =
        line.replace(Regex("^.*at "), "")

    /** 移除括号中的行号信息，如 (SourceFile:42) */
    private fun stripLineNumber(line: String): String =
        line.replace(Regex("\\(.+?\\)"), "")
}
