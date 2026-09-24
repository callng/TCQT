package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.invoke
import com.owo233.tcqt.core.reflect.setObject

@RegisterAction
object ForcedABTest : Feature(
    key = "forced_to_ab",
    name = "AB测试强制转组",
    desc = "在AB测试中强制转到指定组，想优先体验某些灰度功能时可以尝试本功能，或者留在对照组。",
    processes = setOf(ActionProcess.ALL),
) {

    private val mode by intOption(
        settingKey = "mode",
        name = "强制模式",
        defaultValue = 1,
        options = listOf("强制A组（对照组）", "强制B组（实验组）"),
    )

    private val forcedConfigIds by stringOption(
        settingKey = "forced_config_ids",
        name = "强制开启的配置 ID / 开关 Key",
        desc = "一行一个。填 Freesia 配置 ID（如 107793）或布尔开关 Key（如 qq_share_panel_config）。" +
            "留空表示不改配置，仅强制 AB 转组。",
        placeholder = "107793\nqq_share_panel_config",
    )

    override fun install() {
        val controllerClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ABTestController")
        val expEntityClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")

        expEntityClz.hookMethodBefore("isExpInvalid") { param ->
            param.result = false
        }

        expEntityClz.hookMethodBefore("isExpOnline") { param ->
            param.result = mode == 2
        }

        expEntityClz.hookMethodBefore(
            "isExpHit",
            String::class.java
        ) { param ->
            param.result = mode == 2
        }

        expEntityClz.hookMethodBefore("getAssignment") { param ->
            val expName = param.thisObject.invoke("getExpName") as String
            if (expName.isNotEmpty()) {
                param.result = assignmentOf(expName)
            }
        }

        expEntityClz.hookMethodBefore(
            "isExperiment",
            String::class.java
        ) { param ->
            param.result = mode == 2
        }

        expEntityClz.hookMethodBefore(
            "isContrast",
            String::class.java
        ) { param ->
            param.result = mode == 1
        }

        expEntityClz.hookMethodBefore("isExperimentNew") { param ->
            param.result = mode == 2
        }

        expEntityClz.hookMethodAfter("getGrayId") { param ->
            val grayId = param.result as? String
            if (grayId.isNullOrEmpty()) {
                val expName = param.thisObject.invoke("getExpName") as? String
                if (!expName.isNullOrEmpty()) param.result = expName
            }
        }

        controllerClz.hookMethodAfter(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val entity = param.result ?: return@hookMethodAfter
            val expName = entity.invoke("getExpName") as? String
            if (!expName.isNullOrEmpty()) {
                entity.setObject("mAssignment", assignmentOf(expName))
                val grayId = entity.getObject("mExpGrayId") as? String
                if (grayId.isNullOrEmpty()) {
                    entity.setObject("mExpGrayId", expName)
                }
            }
            param.result = entity
        }

        installConfigHooks()
    }

    /** 按当前模式生成 A/B 赋值串。 */
    private fun assignmentOf(expName: String): String =
        "${expName}_${if (mode == 1) "A" else "B"}"

    private fun installConfigHooks() {
        val ids = forcedConfigIds
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (ids.isEmpty()) return

        val implClz = runCatching {
            loadOrThrow("com.tencent.mobileqq.unitedconfig_android.api.impl.UnitedConfigManagerImpl")
        }.getOrNull() ?: return

        implClz.hookMethodBefore(
            name = "isSwitchOn",
            String::class.java,
            null
        ) { param ->
            val key = param.args.getOrNull(0) as? String ?: return@hookMethodBefore
            if (key in ids) param.result = true
        }

        implClz.hookMethodBefore(
            "loadConfig",
            String::class.java
        ) { param ->
            val id = param.args.getOrNull(0) as? String ?: return@hookMethodBefore
            if (id !in ids) return@hookMethodBefore
            runCatching {
                val parserClz = loadOrThrow(parserClassOf(id))
                val parser = parserClz.getDeclaredConstructor().newInstance()
                val defaultConfig = parserClz.getMethod("defaultConfig").invoke(parser)
                if (defaultConfig != null) param.result = defaultConfig
            }
        }
    }

    /**
     * 把 Freesia 配置 ID 映射到注册的解析器类。
     */
    private fun parserClassOf(id: String): String {
        val implClz = loadOrThrow(
            "com.tencent.mobileqq.unitedconfig_android.api.impl.UnitedConfigManagerImpl"
        )
        for (fieldName in arrayOf("injectParsersConfig", "injectParsersConfigNoLogin")) {
            val map = runCatching {
                val field = implClz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.get(null) as? Map<*, *>
            }.getOrNull() ?: continue
            val value = map[id] as? String
            if (!value.isNullOrEmpty()) return value
        }
        error("未注册的配置 ID: $id")
    }
}
