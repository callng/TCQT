package com.owo233.tcqt.internals.setting

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.log.Log
import io.fastkv.FastKV
import kotlin.reflect.KProperty

internal object TCQTSetting {

    private val config: FastKV by lazy {
        val path = "${HookEnv.moduleDataPath}/global/setting"
        FastKV.Builder(path, TCQTBuild.APP_NAME).build()
    }

    val settingMap: HashMap<String, Setting<out Any>> by lazy {
        GeneratedSettingList.SETTING_MAP
    }

    fun clearAll() {
        config.clear()
    }

    fun containsKey(key: String): Boolean {
        return config.contains(key)
    }

    fun getAllKeys(): MutableSet<String> {
        return config.all.keys
    }

    fun getRawString(key: String, def: String = ""): String {
        return config.getString(key, def) ?: ""
    }

    fun putRawString(key: String, value: String) {
        config.putString(key, value)
    }

    fun remove(key: String) {
        config.remove(key)
    }

    inline fun <reified T : Any> getValue(key: String): T? {
        return runCatching {
            val setting = settingMap[key]
            if (setting != null) {
                val requestedType = inferSettingType<T>()
                val isCompatible = setting.type == requestedType ||
                        (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                        (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return null
                }
                @Suppress("UNCHECKED_CAST")
                return (setting as Setting<T>).getValue()
            }

            val storedType = getStoredType(key)
            if (storedType != null) {
                val requestedType = inferSettingType<T>()
                if (storedType != requestedType) {
                    Log.e("Type mismatch for key: $key, stored: $storedType, requested: $requestedType")
                    return null
                }
                return readFromStorageByType<T>(key, storedType)
            }

            null
        }.onFailure {
            Log.e("Failed to get value for key: $key", it)
        }.getOrNull()
    }

    inline fun <reified T : Any> setValue(key: String, value: T) {
        runCatching {
            val setting = settingMap[key]
            if (setting != null) {
                val requestedType = inferSettingType<T>()
                val isCompatible = setting.type == requestedType ||
                        (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                        (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return
                }
                @Suppress("UNCHECKED_CAST")
                (setting as Setting<T>).setValue(value)
                return
            }

            val type = inferSettingType<T>()
            saveStoredType(key, type)
            writeToStorage(key, value)
        }.onFailure {
            Log.e("Failed to set value for key: $key", it)
        }
    }

    private fun getStoredType(key: String): SettingType? {
        val typeKey = "__type__$key"
        val typeString = config.getString(typeKey, null) ?: return null
        return when (typeString) {
            "BOOLEAN" -> SettingType.BOOLEAN
            "INT" -> SettingType.INT
            "INT_MULTI" -> SettingType.INT_MULTI
            "STRING" -> SettingType.STRING
            else -> null
        }
    }

    private fun saveStoredType(key: String, type: SettingType) {
        val typeKey = "__type__$key"
        config.putString(typeKey, type.name)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> readFromStorageByType(key: String, type: SettingType): T? {
        return when (type) {
            SettingType.BOOLEAN -> config.getBoolean(key, false) as T
            SettingType.INT, SettingType.INT_MULTI -> config.getInt(key, 0) as T
            SettingType.STRING -> (config.getString(key, null) ?: "") as T
        }
    }

    private inline fun <reified T : Any> writeToStorage(key: String, value: T) {
        when (T::class) {
            Boolean::class -> config.putBoolean(key, value as Boolean)
            Int::class -> config.putInt(key, value as Int)
            String::class -> config.putString(key, value.toString())
            else -> Log.e("Unsupported type for key: $key, type: ${T::class}")
        }
    }

    private inline fun <reified T : Any> inferSettingType(): SettingType =
        when (T::class) {
            Boolean::class -> SettingType.BOOLEAN
            Int::class -> SettingType.INT
            String::class -> SettingType.STRING
            else -> throw IllegalArgumentException("Unsupported setting type: ${T::class}")
        }

    enum class SettingType {
        BOOLEAN, INT, STRING, INT_MULTI
    }

    class Setting<T : Any>(
        val key: String,
        val type: SettingType,
        val default: T? = null
    ) {
        @Suppress("UNCHECKED_CAST")
        fun getValue(): T {
            return when (type) {
                SettingType.BOOLEAN -> config.getBoolean(key, default as? Boolean ?: false)
                SettingType.INT, SettingType.INT_MULTI -> config.getInt(key, default as? Int ?: 0)
                SettingType.STRING -> config.getString(key, default as? String ?: "") ?: ""
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        fun setValue(value: T) {
            when (type) {
                SettingType.BOOLEAN -> config.putBoolean(
                    key,
                    value as? Boolean ?: runCatching { value.toString().toBooleanStrict() }
                        .getOrDefault(false)
                )

                SettingType.INT, SettingType.INT_MULTI -> config.putInt(
                    key,
                    value as? Int ?: runCatching { value.toString().toInt() }
                        .getOrDefault(0)
                )

                SettingType.STRING -> config.putString(key, value.toString())
            }
        }

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
            return getValue()
        }

        operator fun setValue(thisRef: Any, property: KProperty<*>?, value: T) {
            setValue(value)
        }
    }
}
