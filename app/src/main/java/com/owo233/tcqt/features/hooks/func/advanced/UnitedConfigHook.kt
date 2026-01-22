package com.owo233.tcqt.features.hooks.func.advanced

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.foundation.extensions.toUtf8ByteArray
import com.owo233.tcqt.features.hooks.base.load
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import java.util.concurrent.ConcurrentHashMap

@RegisterAction
@RegisterSetting(
    key = "united_config_hook",
    name = "统一配置Hook",
    type = SettingType.BOOLEAN,
    desc = "仅高级用户使用，针对'UnitedConfig'处理，本功能与「AB测试强制转B组」有本质上的不同，它覆盖了前者未处理到的配置项。",
    hasTextAreas = true,
    uiTab = "高级"
)
@RegisterSetting(
    key = "united_config_hook.string.saveConfig",
    name = "保存的配置",
    type = SettingType.STRING,
    textAreaPlaceholder = "s:<string>:<string>\nb:<string>:<boolean>\ne.g: b:i_like_you:true\n一行一个配置项"
)
class UnitedConfigHook : IAction {

    private val configClass by lazy { load("com.tencent.freesia.UnitedConfig")!! }

    override fun onRun(ctx: Context, process: ActionProcess) {
        setupUnitedConfigHook()
    }

    private fun setupUnitedConfigHook() {
        configClass.hookAfterMethod(
            "isSwitchOn",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val key = param.args[1] as? String ?: return@hookAfterMethod
            configMap["b" to key]?.let { value ->
                safeParseBoolean(key, value)?.let { parsed ->
                    param.result = parsed
                }
            }
        }

        configClass.hookAfterMethod(
            "loadRawConfig",
            String::class.java,
            String::class.java,
            ByteArray::class.java
        ) { param ->
            val key = param.args[1] as? String ?: return@hookAfterMethod
            configMap["s" to key]?.let { value ->
                param.result = value.toUtf8ByteArray()
            }
        }
    }

    private fun safeParseBoolean(key: String, value: String): Boolean? {
        return value.lowercase().toBooleanStrictOrNull().also {
            if (it == null) {
                Log.e("UnitedConfigHook: Invalid boolean value for key: $key, value: $value")
            }
        }
    }

    companion object {
        private val configString by lazy {
            GeneratedSettingList.getString(GeneratedSettingList.UNITED_CONFIG_HOOK_STRING_SAVECONFIG)
        }

        private val configMap: Map<Pair<String, String>, String> by lazy {
            val map = ConcurrentHashMap<Pair<String, String>, String>()
            configString.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val parts = line.split(":", limit = 3)
                    if (parts.size >= 3) {
                        val type = parts[0]
                        val name = parts[1]
                        val value = parts[2]
                        if (type == "b" || type == "s") {
                            map[type to name] = value
                        }
                    }
                }
            map
        }
    }

    override val key: String get() = GeneratedSettingList.UNITED_CONFIG_HOOK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
