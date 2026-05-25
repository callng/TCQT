package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.ext.toUtf8ByteArray
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.log.Log
import java.util.concurrent.ConcurrentHashMap

/*@RegisterAction*/

class UnitedConfigHook : IAction {

    override val name: String get() = "统一配置Hook"
    override val desc: String get() = "仅高级用户使用，针对'UnitedConfig'处理，本功能与「AB测试强制转B组」有本质上的不同，它覆盖了前者未处理到的配置项。"
    override val uiTab: String get() = "高级"
    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting(
                "united_config_hook.string.saveConfig",
                "保存的配置",
                "",
                "",
                "s:<string>:<string>\nb:<string>:<boolean>\ne.g: b:i_like_you:true\n一行一个配置项",
                false
            ),
        )

    private val configClass by lazy { load("com.tencent.freesia.UnitedConfig")!! }

    override fun onRun(app: Application, process: ActionProcess) {
        setupUnitedConfigHook()
    }

    private fun setupUnitedConfigHook() {
        configClass.hookMethodBefore(
            "isSwitchOn",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val key = param.args[1] as? String ?: return@hookMethodBefore
            configMap["b" to key]?.let { value ->
                safeParseBoolean(key, value)?.let { parsed ->
                    param.result = parsed
                }
            }
        }

        configClass.hookMethodBefore(
            "loadRawConfig",
            String::class.java,
            String::class.java,
            ByteArray::class.java
        ) { param ->
            val key = param.args[1] as? String ?: return@hookMethodBefore
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
            TCQTSetting.getString("united_config_hook.string.saveConfig")
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

    override val key: String get() = "united_config_hook"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
