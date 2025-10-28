package com.owo233.tcqt.internals.setting

import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.moduleClassLoader
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.MMKVUtils
import com.tencent.mmkv.MMKV
import mqq.app.MobileQQ
import oicq.wlogin_sdk.tools.MD5
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.reflect.KProperty

internal object TCQTSetting {

    val dataDir = MobileQQ.getContext().getExternalFilesDir(null)!!
        .parentFile!!.resolve("Tencent/TCQT").also {
            it.mkdirs()
        }

    private val config: MMKV get() = MMKVUtils.mmkvWithId("TCQT")

    val settingMap: HashMap<String, Setting<out Any>> by lazy {
        GeneratedSettingList.SETTING_MAP
    }

    val settingUrl: String get() = "http://tcqt.qq.com/"

    fun getSettingHtml(): String {
        val localFile = dataDir.resolve("index.html")
        val assetPath = "rez/index.html"

        val assetContent = openAsset(assetPath).readText()
        val assetMd5 = MD5.toMD5Byte(assetContent)

        val localContent = if (localFile.exists()) localFile.readText() else null
        val localMd5 = localContent?.let { MD5.toMD5Byte(it) }

        if (localMd5 == null || !localMd5.contentEquals(assetMd5)) {
            localFile.writeText(assetContent)
            return assetContent
        }

        return localContent
    }

    inline fun <reified T : Any> getValue(key: String): T? {
        return runCatching {
            getSetting<T>(key).getValue(null, null)
        }.onFailure {
            Log.e("Failed to get value for key: $key", it)
        }.getOrNull()
    }

    inline fun <reified T : Any> setValue(key: String, value: T) {
        runCatching {
            getSetting<T>(key).setValue(this, null, value)
        }.onFailure {
            Log.e("Failed to set value for key: $key", it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> getSetting(key: String): Setting<T> {
        return settingMap.getOrPut(key) {
            Setting(key, inferSettingType<T>(), null)
        } as Setting<T>
    }

    private inline fun <reified T : Any> inferSettingType(): SettingType =
        when (T::class) {
            Boolean::class -> SettingType.BOOLEAN
            Int::class     -> SettingType.INT
            String::class  -> SettingType.STRING
            else           -> throw IllegalArgumentException("Unsupported setting type: ${T::class}")
        }

    enum class SettingType {
        BOOLEAN, INT, STRING
    }

    class Setting<T: Any>(
        val key: String,
        val type: SettingType,
        val default: T? = null
    ) {
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(thisRef: Any?, property: KProperty<*>?): T {
            return when (type) {
                SettingType.BOOLEAN -> config.getBoolean(key, default as? Boolean ?: false)
                SettingType.INT     -> config.getInt(key, default as? Int ?: 0)
                SettingType.STRING  -> config.getString(key, default as? String ?: "") ?: ""
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        operator fun setValue(thisRef: Any, property: KProperty<*>?, value: T) {
            when (type) {
                SettingType.BOOLEAN -> config.putBoolean(
                    key,
                    value as? Boolean ?: runCatching { value.toString().toBooleanStrict() }
                        .getOrDefault(false)
                )
                SettingType.INT     -> config.putInt(
                    key,
                    value as? Int ?: runCatching { value.toString().toInt() }
                        .getOrDefault(0)
                )
                SettingType.STRING  -> config.putString(key, value.toString())
            }
        }
    }

    private fun openAsset(fileName: String): InputStream {
        return moduleClassLoader.getResourceAsStream("assets/${fileName}")
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
