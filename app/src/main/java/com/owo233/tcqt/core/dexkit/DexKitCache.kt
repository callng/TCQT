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
        val hostCode: DexKitHostCode,
        val previousCacheUsable: Boolean,
    )

    private var generation = 0L

    private var hostCode = DexKitHostCode(isHotPatched = false)

    val isHotPatched: Boolean get() = hostCode.isHotPatched

    var isHostCodeMatched = false
        private set

    var isVersionMatched = false
        private set

    var isHostVersionMatched = false
        private set

    var isModuleVersionMatched = false
        private set

    private const val KEY_HOST_VER = "__host_ver"
    private const val KEY_MODULE_VER = "__module_ver"
    private const val KEY_HOST_CODE = "__host_code"

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
    fun initCache(hostCode: DexKitHostCode): Boolean {
        this.hostCode = hostCode
        if (ProcUtil.isMain) {
            Log.i("DexKit 使用${if (isHotPatched) "热更新" else "普通"}缓存: $cacheName")
            if (isHotPatched && hostCode.fingerprint == null) {
                Log.w("DexKit 无法识别当前补丁 DEX，保守地全量扫描")
            }
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
            isHostCodeMatched = false
            return false
        }

        val cachedHostVer = all[KEY_HOST_VER]?.toLongOrNull()
        val cachedModuleVer = all[KEY_MODULE_VER]?.toLongOrNull()

        cacheMap = all
            .filterKeys { it != KEY_HOST_VER && it != KEY_MODULE_VER && it != KEY_HOST_CODE }
            .toMap()

        val hostMatched = cachedHostVer == HookEnv.versionCode
        val moduleMatched = cachedModuleVer == TCQTBuild.VER_CODE.toLong()
        isHostCodeMatched = hostCode.matches(all[KEY_HOST_CODE])
        isHostVersionMatched = hostMatched
        isModuleVersionMatched = moduleMatched
        this.isVersionMatched = hostMatched && moduleMatched && isHostCodeMatched
        if (ProcUtil.isMain) {
            Log.i("DexKit 缓存校验: 宿主=$hostMatched, 模块=$moduleMatched, 代码=$isHostCodeMatched, 补丁=${hostCode.fingerprint?.take(12) ?: "无/未知"}")
        }
        return isVersionMatched
    }

    @Synchronized
    fun snapshotForScan() = ScanSnapshot(
        cacheName, generation, cacheMap.toMap(), isVersionMatched, hostCode,
        previousCacheUsable = isHostVersionMatched && isHostCodeMatched,
    )

    @Synchronized
    fun saveCache(snapshot: ScanSnapshot, values: Map<String, String>): Boolean {
        check(ProcUtil.isMain) { "Only the main process may write DexKit scan results" }
        if (snapshot.name != cacheName || snapshot.generation != generation || snapshot.hostCode != hostCode) return false
        val persisted = values + mapOf(
            KEY_HOST_VER to HookEnv.versionCode.toString(),
            KEY_MODULE_VER to TCQTBuild.VER_CODE.toString(),
            KEY_HOST_CODE to hostCode.fingerprint.orEmpty(),
        )
        if (!storage.write(cacheName, snapshot.generation, persisted)) return false
        cacheMap = values.toMap()
        isHostVersionMatched = true
        isModuleVersionMatched = true
        isHostCodeMatched = hostCode.matches(persisted[KEY_HOST_CODE])
        isVersionMatched = isHostCodeMatched
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
            isHostCodeMatched = false
            isVersionMatched = false
            Log.i("DexKit 已清空 debug/release 的普通、热更新和旧版缓存")
            true
        }.getOrElse {
            Log.e("清空 DexKit 缓存失败", it)
            false
        }
    }
}
