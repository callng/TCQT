package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.proto.GlobalJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 脚本数据存储：每个脚本在 `config/<配置名>.json` 里保存一个扁平的键值对象。
 *
 * 与 QFun 的脚本目录约定保持一致，脚本迁移过来时配置可以直接复用。
 */
internal object ScriptConfig {

    private val locks = mutableMapOf<String, Any>()

    private fun lockOf(path: String): Any = synchronized(locks) { locks.getOrPut(path) { Any() } }

    private fun fileOf(configDir: File, configName: String): File {
        val safe = configName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(configDir, if (safe.endsWith(".json")) safe else "$safe.json")
    }

    private fun read(file: File): MutableMap<String, JsonElement> = runCatching {
        if (!file.exists()) return mutableMapOf()
        val obj = GlobalJson.parseToJsonElement(file.readText()) as? JsonObject
        obj?.toMutableMap() ?: mutableMapOf()
    }.onFailure {
        Log.e("脚本配置读取失败: ${file.name}", it)
    }.getOrDefault(mutableMapOf())

    private fun write(file: File, map: Map<String, JsonElement>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(GlobalJson.encodeToString(JsonObject.serializer(), JsonObject(map)))
        }.onFailure { Log.e("脚本配置写入失败: ${file.name}", it) }
    }

    private fun put(configDir: File, configName: String, key: String, value: JsonElement) {
        val file = fileOf(configDir, configName)
        synchronized(lockOf(file.absolutePath)) {
            val map = read(file)
            map[key] = value
            write(file, map)
        }
    }

    private fun get(configDir: File, configName: String, key: String): JsonElement? {
        val file = fileOf(configDir, configName)
        return synchronized(lockOf(file.absolutePath)) { read(file)[key] }
    }

    fun put(configDir: File, configName: String, key: String, value: String) =
        put(configDir, configName, key, JsonPrimitive(value))

    fun put(configDir: File, configName: String, key: String, value: Int) =
        put(configDir, configName, key, JsonPrimitive(value))

    fun put(configDir: File, configName: String, key: String, value: Long) =
        put(configDir, configName, key, JsonPrimitive(value))

    fun put(configDir: File, configName: String, key: String, value: Boolean) =
        put(configDir, configName, key, JsonPrimitive(value))

    fun get(configDir: File, configName: String, key: String, fallback: String): String =
        (get(configDir, configName, key) as? JsonPrimitive)?.content ?: fallback

    fun get(configDir: File, configName: String, key: String, fallback: Int): Int =
        (get(configDir, configName, key) as? JsonPrimitive)?.intOrNull ?: fallback

    fun get(configDir: File, configName: String, key: String, fallback: Long): Long =
        (get(configDir, configName, key) as? JsonPrimitive)?.longOrNull ?: fallback

    fun get(configDir: File, configName: String, key: String, fallback: Boolean): Boolean =
        (get(configDir, configName, key) as? JsonPrimitive)?.booleanOrNull ?: fallback
}
