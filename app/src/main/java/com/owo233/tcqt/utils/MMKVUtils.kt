package com.owo233.tcqt.utils

import com.tencent.mmkv.MMKV
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object MMKVUtils {

    private const val SINGLE_PROCESS_MODE = 1
    private const val MULTI_PROCESS_MODE = 2
    private val handles = ConcurrentHashMap.newKeySet<Long>()

    private val checkProcessModeMethod: Method by lazy {
        MMKV::class.java.getDeclaredMethod("checkProcessMode", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
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
        MMKV::class.java.getDeclaredConstructor(Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
    }

    private fun checkProcessMode(handle: Long): Boolean =
        checkProcessModeMethod.invoke(null, handle) as Boolean

    private fun getMMKVHandle(id: String, processMode: Int): Long =
        getMMKVWithIDMethod.invoke(null, id, processMode, null, null, 0L) as Long

    private fun createMMKV(nativeHandle: Long): MMKV =
        mmkvConstructor.newInstance(nativeHandle)

    private fun getOrCreateMMKV(processMode: Int, nativeHandle: Long, id: String): MMKV {
        require(nativeHandle != 0L) { "Failed to create MMKV instance [$id] in JNI" }

        synchronized(handles) {
            if (handles.add(nativeHandle)) {
                if (!checkProcessMode(nativeHandle)) {
                    val msg = when (processMode) {
                        SINGLE_PROCESS_MODE -> "Opening multi-process MMKV [$id] with SINGLE_PROCESS_MODE!"
                        else -> "Opening MMKV [$id] with MULTI_PROCESS_MODE, " +
                                "but it's already opened as SINGLE_PROCESS_MODE elsewhere!"
                    }
                    throw IllegalArgumentException(msg)
                }
            }
        }

        return createMMKV(nativeHandle)
    }

    fun mmkvWithId(id: String, multiProcess: Boolean = true): MMKV {
        val mode = if (multiProcess) MULTI_PROCESS_MODE else SINGLE_PROCESS_MODE
        val handle = getMMKVHandle(id, mode)
        return getOrCreateMMKV(mode, handle, id)
    }
}
