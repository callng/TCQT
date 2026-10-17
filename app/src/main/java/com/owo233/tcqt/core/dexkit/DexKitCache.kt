package com.owo233.tcqt.core.dexkit

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import io.fastkv.FastKV
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Method

internal object DexKitCache {

    @Volatile
    var cacheMap: Map<String, String> = emptyMap()
        private set

    internal data class ScanSnapshot(
        val name: String,
        val generation: Long,
        val values: Map<String, String>,
        val versionMatched: Boolean,
    )

    private var generation = 0L

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

    private val cacheName: String
        get() = "DexKitCache_${getModuleBuildType()}_${if (isHotPatched) "hot_patch" else "normal"}"

    private val storage by lazy {
        val directory = File(HookEnv.moduleDataPath, "global/dexkit")
        DexKitCacheStorage(directory) { name ->
            val kv = FastKV.Builder(directory.absolutePath, name).build()
            object : DexKitCacheBackend {
                override fun read() = kv.all.mapValues { it.value.toString() }
                override fun replace(values: Map<String, String>) {
                    kv.clear()
                    kv.putAll(values)
                    kv.force()
                    check(read() == values) { "DexKit cache write verification failed" }
                }
                override fun close() = kv.close()
            }
        }
    }

    @Synchronized
    fun initCache(isHotPatched: Boolean): Boolean {
        this.isHotPatched = isHotPatched
        if (ProcUtil.isMain) {
            Log.i("DexKit 使用${if (isHotPatched) "热更新" else "普通"}缓存: $cacheName")
        }
        // 切换到空的缓存分区时也必须清空内存，不能残留另一种代码来源的描述符。
        cacheMap = emptyMap()
        val stored = storage.read(cacheName)
        generation = stored.generation
        val all = stored.values
        if (all.isEmpty()) {
            isVersionMatched = false
            isHostVersionMatched = false
            isModuleVersionMatched = false
            return false
        }

        val cachedHostVer = all[KEY_HOST_VER]?.toLongOrNull()
        val cachedModuleVer = all[KEY_MODULE_VER]?.toLongOrNull()

        cacheMap = all
            .filterKeys { it != KEY_HOST_VER && it != KEY_MODULE_VER }
            .toMap()

        val hostMatched = cachedHostVer == HookEnv.versionCode
        val moduleMatched = cachedModuleVer == TCQTBuild.VER_CODE.toLong()
        isHostVersionMatched = hostMatched
        isModuleVersionMatched = moduleMatched
        this.isVersionMatched = hostMatched && moduleMatched
        return isVersionMatched
    }

    @Synchronized
    fun snapshotForScan() = ScanSnapshot(cacheName, generation, cacheMap.toMap(), isVersionMatched)

    @Synchronized
    fun saveCache(snapshot: ScanSnapshot, values: Map<String, String>): Boolean {
        check(ProcUtil.isMain) { "Only the main process may write DexKit scan results" }
        if (snapshot.name != cacheName || snapshot.generation != generation) return false
        val persisted = values + mapOf(
            KEY_HOST_VER to HookEnv.versionCode.toString(),
            KEY_MODULE_VER to TCQTBuild.VER_CODE.toString(),
        )
        if (!storage.write(cacheName, snapshot.generation, persisted)) return false
        cacheMap = values.toMap()
        isHostVersionMatched = true
        isModuleVersionMatched = true
        isVersionMatched = true
        Log.i("DexKit 已保存 $cacheName: 成功 ${values.count { it.value.isNotEmpty() }} / ${values.size}")
        return true
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

    @Synchronized
    fun clearCache(): Boolean {
        return runCatching {
            generation = storage.clear()
            cacheMap = emptyMap()
            isHostVersionMatched = false
            isModuleVersionMatched = false
            isVersionMatched = false
            Log.i("DexKit 已清空 debug/release 的普通、热更新和旧版缓存")
            true
        }.getOrElse {
            Log.e("清空 DexKit 缓存失败", it)
            false
        }
    }
}
