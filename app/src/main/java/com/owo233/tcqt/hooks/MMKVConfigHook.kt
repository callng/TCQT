package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.hookAfterMethod
import java.util.concurrent.ConcurrentHashMap

@RegisterAction
@RegisterSetting(
    key = "mmkv_config_hook",
    name = "MMKV配置Hook",
    type = SettingType.BOOLEAN,
    desc = $$"仅高级用户使用，可以使用$uin或$uid表示当前登录的账号，暂时只支持处理Boolean类型的配置。",
    hasTextAreas = true,
    uiTab = "高级",
    uiOrder = 109
)
@RegisterSetting(
    key = "mmkv_config_hook.string.saveConfig",
    name = "保存的配置",
    type = SettingType.STRING,
    textAreaPlaceholder = $$"<key>:<boolean>\ne.g: FROM_EXP$uin:true\n一行一个配置项"
)
class MMKVConfigHook : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val clazz = load("com.tencent.mobileqq.qmmkv.v2.MMKVOptionEntityV2")
            ?: error("MMKVOptionEntityV2 class not found...")

        val mmapIdField = clazz.getDeclaredField("mmapId").apply {
            isAccessible = true
        }

        clazz.hookAfterMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.java,
            Boolean::class.java
        ) { param ->
            val thisObj = param.thisObject
            val mmapId= mmapIdField.get(thisObj) as? String ?: return@hookAfterMethod
            if (mmapId != "common_mmkv_configurations") return@hookAfterMethod

            val key = param.args[0] as String
            configMap[key]?.let { value ->
                safeParseBoolean(key, value)?.let { parsed ->
                    param.result = parsed
                }
            }
        }
    }

    private fun safeParseBoolean(key: String, value: String): Boolean? {
        return value.lowercase().toBooleanStrictOrNull().also {
            if (it == null) {
                Log.e("MMKVConfigHook: Invalid boolean value for key: $key, value: $value")
            }
        }
    }

    companion object {

        private val configString by lazy {
            GeneratedSettingList.getString(GeneratedSettingList.MMKV_CONFIG_HOOK_STRING_SAVECONFIG)
        }

        private val configMap: Map<String, String> by lazy {
            val map = ConcurrentHashMap<String, String>()
            configString.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].replaceUinPlaceholder()
                        val value = parts[1]
                        map[key] = value
                    }
                }
            map
        }

        private fun String.replaceUinPlaceholder(): String {
            val replacements = try {
                mapOf(
                    $$"$uin" to QQInterfaces.currentUin.toString(),
                    $$"$uid" to QQInterfaces.currentUid,
                    $$"$guid" to QQInterfaces.guid
                )
            } catch (_: NullPointerException) {
                return this
            }

            return replacements.entries.fold(this) { acc, (k, v) ->
                acc.replace(k, v)
            }
        }
    }

    override val key: String get() = GeneratedSettingList.MMKV_CONFIG_HOOK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
