package com.owo233.tcqt.utils

import android.content.Context
import android.net.Uri
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.log.Log
import com.tencent.mmkv.MMKV
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigBackupManager {

    private const val BACKUP_MARKER = "TCQT_CONFIG_BACKUP"
    private const val BACKUP_VERSION = 1

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class BackupData(
        val marker: String = BACKUP_MARKER,
        val version: Int = BACKUP_VERSION,
        val timestamp: Long = System.currentTimeMillis(),
        val moduleName: String = TCQTBuild.APP_NAME,
        val moduleVersion: String = TCQTBuild.VER_NAME,
        val settings: List<SettingItem>
    )

    @Serializable
    private data class SettingItem(
        val key: String,
        val type: String,
        val value: String
    )

    sealed class RestoreResult {
        data class Success(val count: Int) : RestoreResult()
        object InvalidFile : RestoreResult()
        object VersionMismatch : RestoreResult()
        data class Error(val exception: Exception) : RestoreResult()
    }

    fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        return "${TCQTBuild.APP_NAME}_Backup_$timestamp.json"
    }

    fun backupConfig(context: Context, uri: Uri): Boolean {
        return try {
            val config = MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME)
            val settings = mutableListOf<SettingItem>()

            val settingMap = GeneratedSettingList.SETTING_MAP
            for ((key, setting) in settingMap) {
                if (!isDefaultValue(config, setting)) {
                    val typeStr = setting.type.name
                    val valueStr = getValueAsString(config, setting)

                    settings.add(SettingItem(key, typeStr, valueStr))
                }
            }

            val backupData = BackupData(settings = settings)
            val jsonStr = json.encodeToString(backupData)

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonStr.toByteArray(Charsets.UTF_8))
            }

            Log.d("Backup successful: ${settings.size} settings saved")
            true
        } catch (e: Exception) {
            Log.e("Backup failed", e)
            false
        }
    }

    private fun isDefaultValue(config: MMKV, setting: TCQTSetting.Setting<out Any>): Boolean {
        return try {
            val currentValue = setting.getValue(config)
            val defaultValue = getDefaultValue(setting)
            currentValue == defaultValue
        } catch (_: Exception) {
            false
        }
    }

    private fun getDefaultValue(setting: TCQTSetting.Setting<out Any>): Any? {
        return setting.default
    }

    fun restoreConfig(context: Context, uri: Uri): RestoreResult {
        return try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return RestoreResult.InvalidFile

            val backupData = json.decodeFromString<BackupData>(jsonStr)

            if (backupData.marker != BACKUP_MARKER) {
                return RestoreResult.InvalidFile
            }

            if (backupData.version > BACKUP_VERSION) {
                return RestoreResult.VersionMismatch
            }

            val config = MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME)
            val settingMap = GeneratedSettingList.SETTING_MAP

            config.clearAll()

            var restoredCount = 0

            for (item in backupData.settings) {
                val setting = settingMap[item.key] ?: continue
                val type = try {
                    TCQTSetting.SettingType.valueOf(item.type)
                } catch (_: IllegalArgumentException) {
                    continue
                }

                if (type != setting.type &&
                    !(type == TCQTSetting.SettingType.INT && setting.type == TCQTSetting.SettingType.INT_MULTI) &&
                    !(type == TCQTSetting.SettingType.INT_MULTI && setting.type == TCQTSetting.SettingType.INT)) {
                    continue
                }

                if (setValueFromString(config, setting, item.value)) {
                    restoredCount++
                }
            }

            Log.d("Restore successful: $restoredCount settings restored")
            RestoreResult.Success(restoredCount)
        } catch (e: Exception) {
            Log.e("Restore failed", e)
            RestoreResult.Error(e)
        }
    }

    private fun getValueAsString(config: MMKV, setting: TCQTSetting.Setting<out Any>): String {
        return when (setting.type) {
            TCQTSetting.SettingType.BOOLEAN -> setting.getValue(config).toString()
            TCQTSetting.SettingType.INT, TCQTSetting.SettingType.INT_MULTI -> setting.getValue(config).toString()
            TCQTSetting.SettingType.STRING -> setting.getValue(config).toString()
        }
    }

    private fun setValueFromString(config: MMKV, setting: TCQTSetting.Setting<out Any>, value: String): Boolean {
        return try {
            when (setting.type) {
                TCQTSetting.SettingType.BOOLEAN -> {
                    val boolValue = value.toBooleanStrict()
                    @Suppress("UNCHECKED_CAST")
                    (setting as TCQTSetting.Setting<Boolean>).setValue(config, boolValue)
                }
                TCQTSetting.SettingType.INT, TCQTSetting.SettingType.INT_MULTI -> {
                    val intValue = value.toInt()
                    @Suppress("UNCHECKED_CAST")
                    (setting as TCQTSetting.Setting<Int>).setValue(config, intValue)
                }
                TCQTSetting.SettingType.STRING -> {
                    @Suppress("UNCHECKED_CAST")
                    (setting as TCQTSetting.Setting<String>).setValue(config, value)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("Failed to set value for key: ${setting.key}", e)
            false
        }
    }
}
