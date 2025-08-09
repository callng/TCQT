package com.owo233.tcqt.internals.setting

import com.owo233.tcqt.utils.MMKVUtils
import com.owo233.tcqt.utils.moduleClassLoader
import com.tencent.mmkv.MMKV
import mqq.app.MobileQQ
import oicq.wlogin_sdk.tools.MD5
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.reflect.KProperty

internal object TCQTSetting {
    const val BROWSER_RESTRICT_MITIGATION: String = "browser_restrict_mitigation"
    const val CHANGE_GUID: String = "change_guid"
    const val DEFAULT_BUBBLE: String = "default_bubble"
    const val DEFAULT_FONT: String = "default_font"
    const val DISABLE_FLASH_PIC: String = "disable_flash_pic"
    const val DISABLE_HOT_PATCH: String = "disable_hot_patch"
    const val DISABLE_REACTION_LIMIT: String = "disable_reaction_limit"
    const val FAKE_MULTI_WINDOW_STATUS: String = "fake_multi_window_status"
    const val FETCH_SERVICE: String = "fetch_service"
    const val FLAG_SECURE_BYPASS: String = "flag_secure_bypass"
    const val LOGIN_CHECK_BOX_DEFAULT: String = "login_check_box_default"
    const val MODULE_UPDATE: String = "module_update"
    const val ONE_CLICK_LIKES: String = "one_click_likes"
    const val POKE_NO_COOL_DOWN: String = "poke_no_cool_down"
    const val REMOVE_QR_LOGIN_CHECK: String = "remove_qr_login_check"
    const val RENAME_BASE_APK: String = "rename_base_apk"
    const val REPLY_NO_AT: String = "reply_no_at"
    const val SKIP_QR_LOGIN_WAIT: String = "skip_qr_login_wait"

    val dataDir = MobileQQ.getContext().getExternalFilesDir(null)!!
        .parentFile!!.resolve("Tencent/TCQT").also {
            it.mkdirs()
        }

    private val config: MMKV get() = MMKVUtils.mmkvWithId("TCQT")

    val settingMap = hashMapOf<String, Setting<out Any>>(
        BROWSER_RESTRICT_MITIGATION to Setting(BROWSER_RESTRICT_MITIGATION, SettingType.BOOLEAN, true),
        CHANGE_GUID to Setting(CHANGE_GUID, SettingType.BOOLEAN, true),
        DEFAULT_BUBBLE to Setting(DEFAULT_BUBBLE, SettingType.BOOLEAN, true),
        DEFAULT_FONT to Setting(DEFAULT_FONT, SettingType.BOOLEAN, true),
        DISABLE_FLASH_PIC to Setting(DISABLE_FLASH_PIC, SettingType.BOOLEAN, true),
        DISABLE_HOT_PATCH to Setting(DISABLE_HOT_PATCH, SettingType.BOOLEAN, false),
        DISABLE_REACTION_LIMIT to Setting(DISABLE_REACTION_LIMIT, SettingType.BOOLEAN, true),
        FAKE_MULTI_WINDOW_STATUS to Setting(FAKE_MULTI_WINDOW_STATUS, SettingType.BOOLEAN, true),
        FETCH_SERVICE to Setting(FETCH_SERVICE, SettingType.BOOLEAN, true),
        FLAG_SECURE_BYPASS to Setting(FLAG_SECURE_BYPASS, SettingType.BOOLEAN, true),
        LOGIN_CHECK_BOX_DEFAULT to Setting(LOGIN_CHECK_BOX_DEFAULT, SettingType.BOOLEAN, true),
        MODULE_UPDATE to Setting(MODULE_UPDATE, SettingType.BOOLEAN, true),
        ONE_CLICK_LIKES to Setting(ONE_CLICK_LIKES, SettingType.BOOLEAN, true),
        POKE_NO_COOL_DOWN to Setting(POKE_NO_COOL_DOWN, SettingType.BOOLEAN, false),
        REMOVE_QR_LOGIN_CHECK to Setting(REMOVE_QR_LOGIN_CHECK, SettingType.BOOLEAN, true),
        RENAME_BASE_APK to Setting(RENAME_BASE_APK, SettingType.BOOLEAN, true),
        REPLY_NO_AT to Setting(REPLY_NO_AT, SettingType.BOOLEAN, true),
        SKIP_QR_LOGIN_WAIT to Setting(SKIP_QR_LOGIN_WAIT, SettingType.BOOLEAN, true)
    )

    val settingUrl: String
        get() = dataDir.resolve("domain").also {
            if (!it.exists()) {
                it.createNewFile()
                it.writeText("127.0.0.1:5315")
            }
        }.readText()

    val settingHtml: String
        get() {
            val localFile = dataDir.resolve("index.html")
            val assetPath = "rez/index.html"

            val assetContent = openAsset(assetPath).readText()
            val assetMd5 = MD5.toMD5Byte(assetContent)

            val localMd5 = if (localFile.exists()) {
                MD5.toMD5Byte(localFile.readText())
            } else {
                null
            }

            return if (localMd5 == null || !localMd5.contentEquals(assetMd5)) {
                localFile.writeText(assetContent)
                assetContent
            } else {
                localFile.readText()
            }
        }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getSetting(key: String): Setting<T> {
        val result = settingMap[key] ?: Setting(key, SettingType.BOOLEAN)
        return result as Setting<T>
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
                SettingType.STRING  -> config.getString(key, default as? String ?: "")!!
            } as T
        }

        @Suppress("UNCHECKED_CAST")
        operator fun setValue(thisRef: Any, property: KProperty<*>?, value: T) {
            when (type) {
                SettingType.BOOLEAN -> config.putBoolean(key, (value as? Boolean) ?: value.toString().toBooleanStrict())
                SettingType.INT     -> config.putInt(key, (value as? Int) ?: value.toString().toInt())
                SettingType.STRING  -> config.putString(key, value.toString())
            }
        }
    }

    fun openAsset(fileName: String): InputStream {
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
