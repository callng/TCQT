package com.owo233.tcqt.foundation.utils

import com.tencent.mmkv.MMKV
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object MMKVUtils {

    private const val SINGLE_PROCESS_MODE = 1
    private const val MULTI_PROCESS_MODE = 2

    private val mmkvCache = ConcurrentHashMap<String, MMKV>()

    private val checkProcessModeMethod: Method by lazy {
        MMKV::class.java.getDeclaredMethod(
            "checkProcessMode",
            Long::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    private val getMMKVWithIDMethod: Method by lazy {
        MMKV::class.java.getDeclaredMethod(
            "getMMKVWithID",
            String::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    private val mmkvConstructor: Constructor<MMKV> by lazy {
        MMKV::class.java.getDeclaredConstructor(
            Long::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    fun mmkvWithId(id: String, multiProcess: Boolean = true): MMKV {
        // 优先命中缓存
        mmkvCache[id]?.let { return it }

        return mmkvCache.computeIfAbsent(id) { _ ->
            val mode = if (multiProcess) MULTI_PROCESS_MODE else SINGLE_PROCESS_MODE

            // 传入 null 作为 rootPath，意味着使用 MMKV 默认路径
            val handle = getMMKVWithIDMethod.invoke(
                null,
                id, mode, null, null, 0L) as Long

            require(handle != 0L) { "Failed to get MMKV handle for [$id]" }

            val isCorrectMode = checkProcessModeMethod.invoke(null, handle) as Boolean
            if (!isCorrectMode) {
                val msg = if (mode == SINGLE_PROCESS_MODE) {
                    "Opening multi-process MMKV [$id] with SINGLE_PROCESS_MODE!"
                } else {
                    "Opening MMKV [$id] with MULTI_PROCESS_MODE but it's already SINGLE!"
                }
                throw IllegalArgumentException(msg)
            }

            mmkvConstructor.newInstance(handle)
        }
    }
}
