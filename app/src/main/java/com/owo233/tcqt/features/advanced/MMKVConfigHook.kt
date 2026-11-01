package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.QQInterfaces
import java.util.concurrent.ConcurrentHashMap

/**
 * MMKV 配置在宿主启动早期就可能被读取（不一定在 onCreate 内），故放在 EARLY。
 */
@RegisterAction(processes = [ActionProcess.ALL], priority = ActionPriority.EARLY)
object MMKVConfigHook : Feature(
    key = "mmkv_config_hook",
    name = "MMKV配置Hook",
    desc = $$"仅高级用户使用，针对'common_mmkv_configurations'处理，可以使用$uin或$uid表示当前登录的账号，暂时只支持处理Boolean类型的配置。",
) {

    private val saveConfig by stringOption(
        settingKey = "string.saveConfig",
        name = "保存的配置",
        placeholder = $$"<key>:<boolean>\ne.g: FROM_EXP$uin:true\n一行一个配置项",
    )

    private val configMap: Map<String, String> by lazy {
        val map = ConcurrentHashMap<String, String>()
        saveConfig.lines()
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

    override fun install() {
        val clazz = load("com.tencent.mobileqq.qmmkv.v2.MMKVOptionEntityV2")
            ?: error("MMKVOptionEntityV2 class not found...")

        val mmapIdField = clazz.getDeclaredField("mmapId").apply {

            isAccessible = true
        }

        clazz.hookMethodBefore(
            "getBoolean",
            String::class.java,
            Boolean::class.java,
            Boolean::class.java
        ) { param ->
            val thisObj = param.thisObject
            val mmapId = mmapIdField.get(thisObj) as? String ?: return@hookMethodBefore
            if (mmapId != "common_mmkv_configurations") return@hookMethodBefore

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

    private fun String.replaceUinPlaceholder(): String {
        val replacements = try {
            mapOf(
                $$"$uin" to QQInterfaces.currentUin,
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
