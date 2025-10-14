package com.owo233.tcqt.utils

import android.content.Context
import com.owo233.tcqt.hooks.base.hostInfo
import com.tencent.mmkv.MMKV
import java.lang.reflect.Method

internal object MMKVUtils {

    const val SINGLE_PROCESS_MODE = 1 // 单进程访问
    const val MULTI_PROCESS_MODE = 2 // 多进程访问

    private lateinit var METHOD_GET_MMKV: Method
    private lateinit var METHOD_INIT_MMKV: Method

    private val mmkvWithIdMethod: Method by lazy {
        val methods = MMKV::class.java.declaredMethods
            .filter { it.isStatic && it.paramCount == 2 && it.returnType == MMKV::class.java }

        val target = when (hostInfo.versionCode) {
            PlatformTools.QQ_9_2_23_30095 -> {
                methods.firstOrNull {
                    it.parameterTypes[0] == Int::class.java &&
                            it.parameterTypes[1] == String::class.java
                }
            }
            else -> {
                methods.firstOrNull {
                    it.parameterTypes[0] == String::class.java &&
                            it.parameterTypes[1] == Int::class.java
                }
            }
        }

        target ?: error("Unable to find MMKV.getMMKVWithID method for version ${hostInfo.versionCode}")
    }

    fun initMMKV(ctx: Context) {
        if (!::METHOD_INIT_MMKV.isInitialized) {
            METHOD_INIT_MMKV = MMKV::class.java.declaredMethods.first {
                it.isStatic && it.paramCount == 1 && it.parameterTypes[0] == Context::class.java
            }
            METHOD_INIT_MMKV.invoke(null, ctx)
        }
    }

    fun defaultMMKV(): MMKV {
        if (!::METHOD_GET_MMKV.isInitialized) {
            METHOD_GET_MMKV = MMKV::class.java.declaredMethods.first {
                it.isStatic && it.paramCount == 0 && it.returnType == MMKV::class.java
            }
        }
        return METHOD_GET_MMKV.invoke(null) as MMKV
    }

    fun mmkvWithId(id: String): MMKV {
        return when (hostInfo.versionCode) {
            PlatformTools.QQ_9_2_23_30095 ->
                mmkvWithIdMethod.invoke(null, MULTI_PROCESS_MODE, id)
            else ->
                mmkvWithIdMethod.invoke(null, id, MULTI_PROCESS_MODE)
        } as MMKV
    }
}
