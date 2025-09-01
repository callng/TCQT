package com.owo233.tcqt.internals.setting

import com.owo233.tcqt.utils.MMKVUtils
import com.owo233.tcqt.hooks.base.moduleClassLoader
import com.tencent.mmkv.MMKV
import mqq.app.MobileQQ
import oicq.wlogin_sdk.tools.MD5
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.reflect.KProperty

internal object TCQTSetting {
    // const val BROWSER_RESTRICT_MITIGATION: String = "browser_restrict_mitigation"
    const val CHANGE_GUID: String = "change_guid"
    const val CUSTOM_DEVICE: String = "custom_device"
    const val CUSTOM_DEVICE_STRING_DEVICE: String = "custom_device_string_device"
    const val CUSTOM_DEVICE_STRING_MODEL: String = "custom_device_string_model"
    const val CUSTOM_DEVICE_STRING_MANUFACTURER: String = "custom_device_string_manufacturer"
    const val CUSTOM_SUBAPPID: String = "custom_subappid"
    const val CUSTOM_SUBAPPID_STRING: String = "custom_subappid_string"
    const val DEFAULT_BUBBLE: String = "default_bubble"
    const val DEFAULT_FONT: String = "default_font"
    const val DISABLE_QQ_CRASH_REPORT_MANAGER: String = "disable_qq_crash_report_manager"
    const val DISABLE_FLASH_PIC: String = "disable_flash_pic"
    const val DISABLE_HOT_PATCH: String = "disable_hot_patch"
    const val DISABLE_REACTION_LIMIT: String = "disable_reaction_limit"
    const val EXCLUDE_SEND_CMD: String = "exclude_send_cmd"
    const val EXCLUDE_SEND_CMD_STRING: String = "exclude_send_cmd_string"
    const val FAKE_MULTI_WINDOW_STATUS: String = "fake_multi_window_status"
    const val FETCH_SERVICE: String = "fetch_service" // 防撤回
    const val FLAG_SECURE_BYPASS: String = "flag_secure_bypass"
    const val FORCE_TABLET_MODE: String = "force_tablet_mode"
    const val LOGIN_CHECK_BOX_DEFAULT: String = "login_check_box_default"
    const val FORCED_TO_B: String = "forced_to_b"
    const val MODULE_UPDATE: String = "module_update"
    const val ONE_CLICK_LIKES: String = "one_click_likes"
    const val REPEAT_MESSAGE: String = "repeat_message"
    const val POKE_NO_COOL_DOWN: String = "poke_no_cool_down"
    const val PIC_TYPE_EMOTICON: String = "pic_type_emoticon"
    const val PTT_FORWARD: String = "ptt_forward"
    const val REMOVE_AD: String = "remove_ad"
    const val REMOVE_QR_LOGIN_CHECK: String = "remove_qr_login_check"
    const val REMOVE_REPLY_MSG_CHECK: String = "remove_reply_msg_check"
    const val RENAME_BASE_APK: String = "rename_base_apk"
    const val REPLY_NO_AT: String = "reply_no_at"
    const val SHOW_HIDE_EMOTICON: String = "show_hide_emoticon"
    const val SHOW_PRECISE_BAN_TIME: String = "show_precise_ban_time"
    const val SKIP_QR_LOGIN_WAIT: String = "skip_qr_login_wait"

    val dataDir = MobileQQ.getContext().getExternalFilesDir(null)!!
        .parentFile!!.resolve("Tencent/TCQT").also {
            it.mkdirs()
        }

    private val config: MMKV get() = MMKVUtils.mmkvWithId("TCQT")

    val settingMap = hashMapOf<String, Setting<out Any>>(
        CHANGE_GUID to Setting(CHANGE_GUID, SettingType.BOOLEAN, true),
        CUSTOM_DEVICE to Setting(CUSTOM_DEVICE, SettingType.BOOLEAN, false),
        CUSTOM_DEVICE_STRING_DEVICE to Setting(CUSTOM_DEVICE_STRING_DEVICE, SettingType.STRING, ""),
        CUSTOM_DEVICE_STRING_MODEL to Setting(CUSTOM_DEVICE_STRING_MODEL, SettingType.STRING, ""),
        CUSTOM_DEVICE_STRING_MANUFACTURER to Setting(CUSTOM_DEVICE_STRING_MANUFACTURER, SettingType.STRING, ""),
        CUSTOM_SUBAPPID to Setting(CUSTOM_SUBAPPID, SettingType.BOOLEAN, false),
        CUSTOM_SUBAPPID_STRING to Setting(CUSTOM_SUBAPPID_STRING, SettingType.STRING, ""),
        DEFAULT_BUBBLE to Setting(DEFAULT_BUBBLE, SettingType.BOOLEAN, false),
        DEFAULT_FONT to Setting(DEFAULT_FONT, SettingType.BOOLEAN, false),
        DISABLE_QQ_CRASH_REPORT_MANAGER to Setting(DISABLE_QQ_CRASH_REPORT_MANAGER, SettingType.BOOLEAN, false),
        DISABLE_FLASH_PIC to Setting(DISABLE_FLASH_PIC, SettingType.BOOLEAN, false),
        DISABLE_HOT_PATCH to Setting(DISABLE_HOT_PATCH, SettingType.BOOLEAN, false),
        DISABLE_REACTION_LIMIT to Setting(DISABLE_REACTION_LIMIT, SettingType.BOOLEAN, false),
        EXCLUDE_SEND_CMD to Setting(EXCLUDE_SEND_CMD, SettingType.BOOLEAN, false),
        EXCLUDE_SEND_CMD_STRING to Setting(EXCLUDE_SEND_CMD_STRING, SettingType.STRING, ""),
        FAKE_MULTI_WINDOW_STATUS to Setting(FAKE_MULTI_WINDOW_STATUS, SettingType.BOOLEAN, false),
        FETCH_SERVICE to Setting(FETCH_SERVICE, SettingType.BOOLEAN, false),
        FLAG_SECURE_BYPASS to Setting(FLAG_SECURE_BYPASS, SettingType.BOOLEAN, false),
        FORCE_TABLET_MODE to Setting(FORCE_TABLET_MODE, SettingType.BOOLEAN, false),
        FORCED_TO_B to Setting(FORCED_TO_B, SettingType.BOOLEAN, false),
        LOGIN_CHECK_BOX_DEFAULT to Setting(LOGIN_CHECK_BOX_DEFAULT, SettingType.BOOLEAN, false),
        MODULE_UPDATE to Setting(MODULE_UPDATE, SettingType.BOOLEAN, false),
        ONE_CLICK_LIKES to Setting(ONE_CLICK_LIKES, SettingType.BOOLEAN, false),
        REPEAT_MESSAGE to Setting(REPEAT_MESSAGE, SettingType.BOOLEAN, false),
        POKE_NO_COOL_DOWN to Setting(POKE_NO_COOL_DOWN, SettingType.BOOLEAN, false),
        PIC_TYPE_EMOTICON to Setting(PIC_TYPE_EMOTICON, SettingType.BOOLEAN, false),
        PTT_FORWARD to Setting(PTT_FORWARD, SettingType.BOOLEAN, false),
        REMOVE_AD to Setting(REMOVE_AD, SettingType.BOOLEAN, false),
        REMOVE_QR_LOGIN_CHECK to Setting(REMOVE_QR_LOGIN_CHECK, SettingType.BOOLEAN, false),
        REMOVE_REPLY_MSG_CHECK to Setting(REMOVE_REPLY_MSG_CHECK, SettingType.BOOLEAN, false),
        RENAME_BASE_APK to Setting(RENAME_BASE_APK, SettingType.BOOLEAN, false),
        REPLY_NO_AT to Setting(REPLY_NO_AT, SettingType.BOOLEAN, false),
        SHOW_HIDE_EMOTICON to Setting(SHOW_HIDE_EMOTICON, SettingType.BOOLEAN, false),
        SHOW_PRECISE_BAN_TIME to Setting(SHOW_PRECISE_BAN_TIME, SettingType.BOOLEAN, false),
        SKIP_QR_LOGIN_WAIT to Setting(SKIP_QR_LOGIN_WAIT, SettingType.BOOLEAN, false)
    )

    val settingUrl: String
        get() {
            val file = dataDir.resolve("domain")
            if (!file.exists()) {
                file.writeText("localhost:5315")
                return "localhost:5315"
            }

            val content = file.readText().trim()
            val host = content.substringBefore(":").trim()

            return if (host != "localhost") {
                file.writeText("localhost:5315")
                "localhost:5315"
            } else {
                content
            }
        }

    val isAppIdDisable: Boolean
        get() {
            val file = dataDir.resolve("isAppIdDisable").also {
                if (!it.exists()) it.createNewFile()
            }
            val content = file.readText().lowercase()
            file.writeText("")
            return content == "off" || content == "true"
        }

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
