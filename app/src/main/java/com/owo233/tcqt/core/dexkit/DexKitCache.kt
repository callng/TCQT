package com.owo233.tcqt.core.dexkit

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import io.fastkv.FastKV
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

internal object DexKitCache {

    var cacheMap = mutableMapOf<String, String>()

    var isHotPatched = false
        private set

    var isVersionMatched = false
        private set

    var isHostVersionMatched = false
        private set

    var isModuleVersionMatched = false
        private set

    private const val KEY_HOST_VER = "__host_ver"
    private const val KEY_MODULE_VER = "__module_ver"

    // 分离两种代码来源；旧的单份缓存可能已被补丁覆盖，不能迁移为普通缓存。
    private val normalKv: FastKV by lazy { buildKv("normal") }
    private val hotPatchKv: FastKV by lazy { buildKv("hot_patch") }

    private val kv: FastKV
        get() = if (isHotPatched) hotPatchKv else normalKv

    private fun buildKv(mode: String): FastKV {
        return FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/dexkit",
            "DexKitCache_${getModuleBuildType()}_$mode"
        ).build()
    }

    private val legacyKv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/dexkit",
            "DexKitCache_${getModuleBuildType()}"
        ).build()
    }

    fun initCache(isHotPatched: Boolean): Boolean {
        this.isHotPatched = isHotPatched
        if (ProcUtil.isMain) {
            Log.i("DexKit 使用${if (isHotPatched) "热更新" else "普通"}缓存")
        }
        // 切换到空的缓存分区时也必须清空内存，不能残留另一种代码来源的描述符。
        cacheMap = mutableMapOf()
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
            normalKv.clear()
            hotPatchKv.clear()
            legacyKv.clear()
            cacheMap.clear()
            isHostVersionMatched = false
            isModuleVersionMatched = false
            isVersionMatched = false
            true
        }.getOrElse { false }
    }
}
