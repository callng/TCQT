package com.owo233.tcqt.utils.dexkit

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.io.IOException
import java.lang.reflect.Method

internal object DexKitCache {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    var cacheMap = mutableMapOf<String, String>()

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private val cacheFile by lazy {
        File(
            "${HookEnv.moduleDataPath}/global/dexkit",
            "CacheMap_${getModuleBuildType()}_${HookEnv.versionCode}_${TCQTBuild.VER_CODE}"
        )
    }

    fun initCache(): Boolean {
        cacheMap = load(cacheFile, mapSerializer)?.toMutableMap() ?: return false
        return true
    }

    fun saveCache() = save(cacheFile, cacheMap.toMap(), mapSerializer)

    fun getClass(key: String): Class<*> =
        cacheMap[key]?.let {
            DexClass(it).getInstance(HookEnv.hostClassLoader)
        } ?: throw ClassNotFoundException(key)

    fun getMethod(key: String): Method =
        cacheMap[key]?.let {
            DexMethod(it).getMethodInstance(HookEnv.hostClassLoader)
        } ?: throw NoSuchMethodException(key)

    private fun <T : Any> load(file: File, serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(serializer, file.readText())
        }.getOrNull()
    }

    private fun <T : Any> save(file: File, obj: T, serializer: KSerializer<T>): Boolean {
        if (!ensureFile(file)) return false
        return runCatching {
            file.writeText(json.encodeToString(serializer, obj))
            true
        }.getOrElse { false }
    }

    private fun ensureFile(file: File): Boolean {
        if (file.exists()) return true
        return try {
            file.parentFile?.let { ensureDir(it) }
            file.createNewFile()
        } catch (_: IOException) {
            false
        }
    }

    private fun ensureDir(dir: File): Boolean {
        return if (!dir.exists()) dir.mkdirs() else dir.isDirectory
    }

    private fun getModuleBuildType(): String {
        return if (TCQTBuild.DEBUG) "d" else "r"
    }

    fun clearCache(): Boolean {
        val dir = cacheFile.parentFile ?: return false
        if (!dir.exists()) return true
        return runCatching {
            dir.listFiles()?.forEach { it.delete() }
            true
        }.getOrElse { false }
    }
}
