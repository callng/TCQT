package com.owo233.tcqt.utils.dexkit

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import io.fastkv.FastKV
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

internal object DexKitCache {

    var cacheMap = mutableMapOf<String, String>()

    private val kv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/dexkit",
            "cache_${getModuleBuildType()}_${HookEnv.versionCode}_${TCQTBuild.VER_CODE}"
        ).build()
    }

    fun initCache(): Boolean {
        val all = kv.all
        if (all.isEmpty()) return false
        cacheMap = all.mapValues { it.value.toString() }.toMutableMap()
        return true
    }

    fun saveCache() {
        kv.clear()
        cacheMap.forEach { (key, value) ->
            kv.putString(key, value)
        }
    }

    fun getClass(key: String): Class<*> =
        cacheMap[key]?.let {
            DexClass(it).getInstance(HookEnv.hostClassLoader)
        } ?: throw ClassNotFoundException(key)

    fun getMethod(key: String): Method =
        cacheMap[key]?.let {
            DexMethod(it).getMethodInstance(HookEnv.hostClassLoader)
        } ?: throw NoSuchMethodException(key)

    private fun getModuleBuildType(): String {
        return if (TCQTBuild.DEBUG) "d" else "r"
    }

    fun clearCache(): Boolean {
        return runCatching {
            kv.clear()
            true
        }.getOrElse { false }
    }
}
