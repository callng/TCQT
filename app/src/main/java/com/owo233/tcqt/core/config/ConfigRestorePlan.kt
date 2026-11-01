package com.owo233.tcqt.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Decode and validate the whole backup before acquiring the storage write lock. */
internal data class ConfigRestorePlan(val values: Map<String, Any>, val registeredKeys: Set<String>) {
    class VersionMismatch : IllegalArgumentException("Unsupported backup version")

    fun applyTo(store: SettingsStore) = store.update { current ->
        // Local metadata (SAF grants, history, unknown future settings) is not a
        // registered feature setting and must survive a backup restore.
        registeredKeys.forEach { current.remove(it); current.remove("__type__$it") }
        current.putAll(values)
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        fun read(input: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "Backup too large" }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
        fun decode(text: String, schema: Map<String, String>): ConfigRestorePlan {
            require(text.length <= MAX_BYTES) { "Backup too large" }
            val root = requireNotNull(Json.parseToJsonElement(text) as? JsonObject) { "Invalid backup" }
            fun string(obj: JsonObject, key: String): String {
                val value = requireNotNull(obj[key] as? JsonPrimitive) { "Missing $key" }
                require(value.isString) { "Invalid $key" }
                return value.content
            }
            require(string(root, "marker") == "TCQT_CONFIG_BACKUP") { "Invalid backup marker" }
            val version = requireNotNull((root["version"] as? JsonPrimitive)?.intOrNull) { "Missing version" }
            if (version > 1) throw VersionMismatch()
            require(version == 1) { "Invalid version" }
            val items = requireNotNull(root["settings"] as? JsonArray) { "Missing settings" }
            require(items.size <= 4096) { "Too many settings" }
            val seen = HashSet<String>()
            val values = LinkedHashMap<String, Any>()
            for (element in items) {
                val item = requireNotNull(element as? JsonObject) { "Invalid setting" }
                val key = string(item, "key")
                require(key.isNotBlank() && key.length <= 256 && seen.add(key)) { "Invalid or duplicate key" }
                val type = string(item, "type")
                val raw = string(item, "value")
                val value: Any = when (type) {
                    "BOOLEAN" -> raw.toBooleanStrict()
                    "INT", "INT_MULTI" -> raw.toInt()
                    "STRING" -> raw
                    else -> throw IllegalArgumentException("Unknown setting type: $type")
                }
                val expected = schema[key] ?: continue
                require(type == expected || type in setOf("INT", "INT_MULTI") && expected in setOf("INT", "INT_MULTI")) {
                    "Type mismatch: $key"
                }
                values[key] = value
            }
            require(items.isEmpty() || values.isNotEmpty()) { "No compatible settings in backup" }
            return ConfigRestorePlan(values.toMap(), schema.keys.toSet())
        }
    }
}
