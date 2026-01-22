package com.owo233.tcqt.foundation.internal.setting

import com.owo233.tcqt.bootstrap.HookEnv.moduleClassLoader
import com.owo233.tcqt.foundation.data.TCQTBuild
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.MMKVUtils
import com.tencent.mmkv.MMKV
import mqq.app.MobileQQ
import oicq.wlogin_sdk.tools.MD5
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.reflect.KProperty

internal object TCQTSetting {

    private var cachedHtml: String? = null

    val dataDir by lazy {
        MobileQQ.getContext().getExternalFilesDir(null)!!
            .parentFile!!.resolve("Tencent/TCQT").also {
                it.mkdirs()
            }
    }

    private val config: MMKV get() = MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME)

    val settingMap: HashMap<String, Setting<out Any>> by lazy {
        GeneratedSettingList.SETTING_MAP
    }

    val settingUrl: String get() = "http://tcqt.qq.com/"

    fun getSettingHtml(): String {
        cachedHtml?.let { return it }

        val assetPath = "rez/index.html"
        val localFile = dataDir.resolve("index.html")

        return runCatching {
            val assetStream = openAsset(assetPath)
                ?: throw IllegalStateException("Asset not found: $assetPath")

            val assetContent = assetStream.readText()
            val assetMd5 = MD5.toMD5Byte(assetContent)

            val localContent = if (localFile.exists()) localFile.readText() else null
            val localMd5 = localContent?.let { MD5.toMD5Byte(it) }

            if (localMd5 == null || !localMd5.contentEquals(assetMd5)) {
                localFile.writeText(assetContent)
                cachedHtml = assetContent
                assetContent
            } else {
                cachedHtml = localContent
                localContent
            }
        }.getOrElse { e ->
            Log.e("Failed to get HTML, returning fallback", e)
            "<html><body><h1>TCQT Error</h1><p>Module updated, please restart QQ.</p></body></html>"
        }
    }

    inline fun <reified T : Any> getValue(key: String): T? {
        return runCatching {
            val setting = settingMap[key]
            if (setting != null) {
                val requestedType = inferSettingType<T>()
                // INT 和 INT_MULTI 互相兼容
                val isCompatible = setting.type == requestedType ||
                    (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                    (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return null
                }
                @Suppress("UNCHECKED_CAST")
                return (setting as Setting<T>).getValue(config)
            }

            // 如果不在 settingMap 中,检查 MMKV 中是否存在类型元数据
            val storedType = getStoredType(key)
            if (storedType != null) {
                val requestedType = inferSettingType<T>()
                if (storedType != requestedType) {
                    Log.e("Type mismatch for key: $key, stored: $storedType, requested: $requestedType")
                    return null
                }
                // 根据存储的类型读取
                return readFromMMKVByType<T>(key, storedType)
            }

            // 如果都没有,说明该 key 不存在
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
                // INT 和 INT_MULTI 互相兼容
                val isCompatible = setting.type == requestedType ||
                    (setting.type == SettingType.INT_MULTI && requestedType == SettingType.INT) ||
                    (setting.type == SettingType.INT && requestedType == SettingType.INT_MULTI)
                if (!isCompatible) {
                    Log.e("Type mismatch for key: $key, expected: ${setting.type}, requested: $requestedType")
                    return
                }
                @Suppress("UNCHECKED_CAST")
                (setting as Setting<T>).setValue(config, value)
                return
            }

            // 如果不在 settingMap 中,保存类型元数据和值
            val type = inferSettingType<T>()
            saveStoredType(key, type)
            writeToMMKV(key, value)
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
    private inline fun <reified T : Any> readFromMMKVByType(key: String, type: SettingType): T? {
        return when (type) {
            SettingType.BOOLEAN -> config.getBoolean(key, false) as T
            SettingType.INT, SettingType.INT_MULTI -> config.getInt(key, 0) as T
            SettingType.STRING  -> (config.getString(key, null) ?: "") as T
        }
    }

    private inline fun <reified T : Any> writeToMMKV(key: String, value: T) {
        when (T::class) {
            Boolean::class -> config.putBoolean(key, value as Boolean)
            Int::class     -> config.putInt(key, value as Int)
            String::class  -> config.putString(key, value.toString())
            else -> Log.e("Unsupported type for key: $key, type: ${T::class}")
        }
    }

    private inline fun <reified T : Any> inferSettingType(): SettingType =
        when (T::class) {
            Boolean::class -> SettingType.BOOLEAN
            Int::class     -> SettingType.INT
            String::class  -> SettingType.STRING
            else           -> throw IllegalArgumentException("Unsupported setting type: ${T::class}")
        }

    enum class SettingType {
        BOOLEAN, INT, STRING, INT_MULTI
    }

    class Setting<T: Any>(
        val key: String,
        val type: SettingType,
        val default: T? = null
    ) {
        @Suppress("UNCHECKED_CAST")
        fun getValue(mmkv: MMKV): T {
            return when (type) {
                SettingType.BOOLEAN -> mmkv.getBoolean(key, default as? Boolean ?: false)
                SettingType.INT, SettingType.INT_MULTI -> mmkv.getInt(key, default as? Int ?: 0)
                SettingType.STRING  -> mmkv.getString(key, default as? String ?: "") ?: ""
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        fun setValue(mmkv: MMKV, value: T) {
            when (type) {
                SettingType.BOOLEAN -> mmkv.putBoolean(
                    key,
                    value as? Boolean ?: runCatching { value.toString().toBooleanStrict() }
                        .getOrDefault(false)
                )
                SettingType.INT, SettingType.INT_MULTI -> mmkv.putInt(
                    key,
                    value as? Int ?: runCatching { value.toString().toInt() }
                        .getOrDefault(0)
                )
                SettingType.STRING  -> mmkv.putString(key, value.toString())
            }
        }

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
            return getValue(config)
        }

        operator fun setValue(thisRef: Any, property: KProperty<*>?, value: T) {
            setValue(config, value)
        }
    }

    private fun openAsset(fileName: String): InputStream? {
        return runCatching {
            moduleClassLoader.getResourceAsStream("assets/${fileName}")
        }.getOrNull()
    }

    private fun InputStream.readText(): String {
        this.use {
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(this, StandardCharsets.UTF_8))
            var line: String? = reader.readLine()
            while (line != null) {
                sb.append(line)
                sb.append("\n")
                line = reader.readLine()
            }
            return sb.toString()
        }
    }
}
