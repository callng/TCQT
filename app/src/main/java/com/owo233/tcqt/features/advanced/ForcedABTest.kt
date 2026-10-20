package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.getObjectOrNull
import com.owo233.tcqt.core.reflect.invoke
import com.owo233.tcqt.core.reflect.setObject

@RegisterAction
object ForcedABTest : Feature(
    key = "forced_to_ab",
    name = "AB测试强制转组",
    desc = "调整已下发的 A/B 实验分组。独立 JSON 灰度配置和服务端权限不属于 AB 分组，不能通过转组开启。",
    processes = setOf(ActionProcess.ALL),
) {

    private val mode by intOption(
        settingKey = "mode",
        name = "强制模式",
        defaultValue = 1,
        options = listOf("强制A组（对照组）", "强制B组（实验组）"),
    )

    private val experimentNames by stringOption(
        settingKey = "experiment_names",
        name = "实验名称",
        desc = "一行一个，精确匹配。留空处理所有已下发的 A/B 格式赋值；其他格式的分组保持原样。",
    )

    private val forcedConfigIds by stringOption(
        settingKey = "forced_config_ids",
        name = "布尔开关 Key / 缺失配置 ID",
        desc = "一行一个。布尔开关设为开启；Freesia 配置 ID 仅在读取结果为空时补解析器默认值，" +
            "默认值不一定开启功能。不支持 loadAsString 读取的 JSON 配置。留空不改配置。",
        placeholder = "107793\nqq_share_panel_config",
    )

    override fun install() {
        // 配置读取与 AB 分组是两条独立路径，不能依赖 AB 类安装成功。
        installConfigHooks()
        val names = ABTestAssignment.parseNames(experimentNames)
        val controllerClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ABTestController")
        val expEntityClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")

        // 只修改实际赋值，保留宿主的有效性、在线状态和参数比较语义。
        // isExpHit/isExperiment/isContrast(String) 都是与传入的完整赋值串比较，
        // 不能按 mode 直接返回 true，否则 A、B、C 甚至空参数会同时命中。
        expEntityClz.hookMethodAfter("getAssignment") { param ->
            if (param.throwable != null) return@hookMethodAfter
            val assignment = forcedAssignment(param.thisObject, param.result as? String, names)
                ?: return@hookMethodAfter
            param.thisObject.setObject("mAssignment", assignment)
            param.result = assignment
        }

        controllerClz.hookMethodAfter(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) { param ->
            if (param.throwable != null) return@hookMethodAfter
            val entity = param.result ?: return@hookMethodAfter
            val assignment = forcedAssignment(entity, entity.getObjectOrNull("mAssignment") as? String, names)
                ?: return@hookMethodAfter
            entity.setObject("mAssignment", assignment)
        }
    }

    private fun forcedAssignment(entity: Any, current: String?, names: Set<String>): String? {
        // 无效实体可能只有实验名，不能为其伪造分组或灰度 ID。
        val appId = entity.getObjectOrNull("mExpAppId") as? String
        if (appId.isNullOrEmpty()) return null
        val name = entity.invoke("getExpName") as? String
        return ABTestAssignment.overrideOf(name, current, mode, names)
    }

    private fun installConfigHooks() {
        val ids = ABTestAssignment.parseNames(forcedConfigIds)
        if (ids.isEmpty()) return

        val implClz = runCatching {
            loadOrThrow("com.tencent.mobileqq.unitedconfig_android.api.impl.UnitedConfigManagerImpl")
        }.getOrNull() ?: return

        implClz.hookMethodBefore(
            name = "isSwitchOn",
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) { param ->
            val key = param.args.getOrNull(0) as? String ?: return@hookMethodBefore
            if (key in ids) param.result = true
        }

        implClz.hookMethodAfter(
            "loadConfig",
            String::class.java
        ) { param ->
            if (param.throwable != null || param.result != null) return@hookMethodAfter
            val id = param.args.getOrNull(0) as? String ?: return@hookMethodAfter
            if (id !in ids) return@hookMethodAfter
            runCatching {
                val parserClz = loadOrThrow(parserClassOf(id))
                val parser = parserClz.getDeclaredConstructor().newInstance()
                val defaultConfig = parserClz.getMethod("defaultConfig").invoke(parser)
                if (defaultConfig != null) param.result = defaultConfig
            }.onFailure { Log.w("ForcedABTest: 配置 $id 默认值读取失败", it) }
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
