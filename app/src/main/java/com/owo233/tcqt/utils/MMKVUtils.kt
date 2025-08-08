package com.owo233.tcqt.utils

import android.content.Context
import com.tencent.mmkv.MMKV
import java.lang.reflect.Method

internal object MMKVUtils {

    const val SINGLE_PROCESS_MODE = 1 // 单进程访问
    const val MULTI_PROCESS_MODE = 2 // 多进程访问

    private lateinit var METHOD_GET_MMKV: Method
    private lateinit var METHOD_GET_MMKV_WITH_ID: Method
    private lateinit var METHOD_INIT_MMKV: Method

    fun initMMKV(ctx: Context) {
        if (!::METHOD_INIT_MMKV.isInitialized) {
            METHOD_INIT_MMKV = MMKV::class.java.declaredMethods.first {
                it.isStatic && it.parameterCount == 1 && it.parameterTypes[0] == Context::class.java
            }
            METHOD_INIT_MMKV.invoke(null, ctx)
        }
    }

    fun defaultMMKV(): MMKV {
        if (!::METHOD_GET_MMKV.isInitialized) {
            METHOD_GET_MMKV = MMKV::class.java.declaredMethods.first {
                it.isStatic && it.parameterCount == 0 && it.returnType == MMKV::class.java
            }
        }
        return METHOD_GET_MMKV.invoke(null) as MMKV
    }

    fun mmkvWithId(id: String): MMKV {
        if (!::METHOD_GET_MMKV_WITH_ID.isInitialized) {
            METHOD_GET_MMKV_WITH_ID = MMKV::class.java.declaredMethods.first {
                it.isStatic && it.parameterCount == 2
                        && it.parameterTypes[0] == String::class.java
                        && it.parameterTypes[1] == Int::class.java
                        && it.returnType == MMKV::class.java
            }
        }
        return METHOD_GET_MMKV_WITH_ID.invoke(null, id, MULTI_PROCESS_MODE) as MMKV
    }
}
