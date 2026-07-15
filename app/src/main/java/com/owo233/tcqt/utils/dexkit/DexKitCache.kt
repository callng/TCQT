package com.owo233.tcqt.utils.dexkit

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import io.fastkv.FastKV
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

internal object DexKitCache {

    var cacheMap = mutableMapOf<String, String>()

    var isVersionMatched = false
        private set

    var isHostVersionMatched = false
        private set

    var isModuleVersionMatched = false
        private set

    private const val KEY_HOST_VER = "__host_ver"
    private const val KEY_MODULE_VER = "__module_ver"

    private val kv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/dexkit",
            "DexKitCache_${getModuleBuildType()}"
        ).build()
    }

    fun initCache(): Boolean {
        val all = kv.all
        if (all.isEmpty()) {
            isVersionMatched = false
            isHostVersionMatched = false
            isModuleVersionMatched = false
            return false
        }

        val cachedHostVer = all[KEY_HOST_VER]?.toString()?.toLongOrNull()
        val cachedModuleVer = all[KEY_MODULE_VER]?.toString()?.toLongOrNull()

        cacheMap = all
            .filterKeys { it != KEY_HOST_VER && it != KEY_MODULE_VER }
            .mapValues { it.value.toString() }
            .toMutableMap()

        val hostMatched = cachedHostVer == HookEnv.versionCode
        val moduleMatched = cachedModuleVer == TCQTBuild.VER_CODE.toLong()
        isHostVersionMatched = hostMatched
        isModuleVersionMatched = moduleMatched
        this.isVersionMatched = hostMatched && moduleMatched
        return isVersionMatched
    }

    fun saveCache() {
        kv.clear()
        kv.putString(KEY_HOST_VER, HookEnv.versionCode.toString())
        kv.putString(KEY_MODULE_VER, TCQTBuild.VER_CODE.toString())
        cacheMap.forEach { (key, value) ->
            kv.putString(key, value)
        }
        isHostVersionMatched = true
        isModuleVersionMatched = true
        isVersionMatched = true
    }

    fun getClass(key: String): Class<*> =
        cacheMap[key]?.takeIf { it.isNotEmpty() }?.let {
            DexClass(it).getInstance(HookEnv.hostClassLoader)
        } ?: throw ClassNotFoundException(key)

    fun getMethod(key: String): Method =
        cacheMap[key]?.takeIf { it.isNotEmpty() }?.let {
            DexMethod(it).getMethodInstance(HookEnv.hostClassLoader)
        } ?: throw NoSuchMethodException(key)

    private fun getModuleBuildType(): String {
        return if (TCQTBuild.DEBUG) "debug" else "release"
    }

    fun clearCache(): Boolean {
        return runCatching {
            kv.clear()
            cacheMap.clear()
            isHostVersionMatched = false
            isModuleVersionMatched = false
            isVersionMatched = false
            true
        }.getOrElse { false }
    }
}
