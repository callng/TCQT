package com.owo233.tcqt.ui.settings

import com.owo233.tcqt.core.action.ActionRegistry
import com.owo233.tcqt.core.config.IntSetting
import com.owo233.tcqt.core.config.IntSliderSetting
import com.owo233.tcqt.core.config.MultiIntSetting
import com.owo233.tcqt.core.config.StringSetting
import com.owo233.tcqt.ui.settings.model.FeatureOptionGroup
import com.owo233.tcqt.ui.settings.model.FeatureSliderField
import com.owo233.tcqt.ui.settings.model.OptionItem
import com.owo233.tcqt.ui.settings.model.SettingFeature
import com.owo233.tcqt.ui.settings.model.TextAreaField
import java.util.concurrent.ConcurrentHashMap

/**
 * 设置界面的功能目录：把运行时注册表转换成 UI 可渲染的表现模型。
 *
 * 这是**唯一**允许依赖 `ui.settings.model`
 * （`SettingFeature` / `FeatureOptionGroup` …）的地方，`core` 不得 import 这些类型。
 */
internal object FeatureCatalog {

    private val initReadyCache = ConcurrentHashMap<String, Boolean>()

    /**
     * 查询功能是否满足执行条件（[com.owo233.tcqt.core.action.ActionSpec.onInit] 是否返回 true），
     * 仅供设置界面判断强制禁用状态；结果带缓存，`onInit` 必须保持为纯条件判断。
     */
    fun isInitReady(key: String): Boolean {
        val action = ActionRegistry.getActionByKey(key) ?: return true
        return initReadyCache.getOrPut(key) {
            runCatching { action.onInit() }.getOrDefault(true)
        }
    }

    fun getSettingDesc(key: String, defaultDesc: String): String {
        val action = ActionRegistry.getActionByKey(key) ?: return defaultDesc
        return action.getSettingDesc(key) ?: defaultDesc
    }

    fun getAllFeatures(): List<SettingFeature> {
        return features
    }

    // 目录只包含当前模块的静态声明；动态描述与开关值仍在每次展示时读取。
    private val features: List<SettingFeature> by lazy {
        val features = mutableListOf<SettingFeature>()
        ActionRegistry.allActionClasses().forEach { actionClass ->
            val action = ActionRegistry.instanceOf(actionClass) ?: return@forEach
            if (action.key.isBlank() || action.hidden) return@forEach

            val textAreas = action.settings
                .filterIsInstance<StringSetting>()
                .filterNot { it.isHide }
                .map { s ->
                    TextAreaField(
                        key = s.key,
                        label = s.name,
                        placeholder = s.placeholder.ifEmpty { "填写${s.name}内容" }
                    )
                }

            val optionGroups = action.settings
                .filter { (it is IntSetting || it is MultiIntSetting) && !it.isHide }
                .map { s ->
                    val options = when (s) {
                        is IntSetting -> s.options
                        is MultiIntSetting -> s.options
                        else -> emptyList()
                    }
                    FeatureOptionGroup(
                        key = s.key,
                        title = s.name,
                        isMulti = s is MultiIntSetting,
                        fallbackValue = s.defaultValue as Int,
                        options = options.mapIndexed { i, label ->
                            OptionItem(label = label, value = i + 1)
                        },
                        forcedSelections = (s as? MultiIntSetting)?.forcedSelections.orEmpty()
                    )
                }

            val sliders = action.settings
                .filterIsInstance<IntSliderSetting>()
                .filterNot { it.isHide }
                .map { s ->
                    FeatureSliderField(
                        key = s.key,
                        label = s.name,
                        min = s.min,
                        max = s.max,
                        step = s.step,
                        suffix = s.suffix,
                        defaultValue = s.defaultValue
                    )
                }

            val categoryPath =
                action.uiTab.trim().split("/").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("基础") }

            features.add(
                SettingFeature(
                    key = action.key,
                    label = action.name,
                    staticDesc = action.desc,
                    order = action.uiOrder,
                    tab = action.uiTab.ifBlank { "基础" },
                    categoryPath = categoryPath,
                    uiType = action.uiType,
                    textAreas = textAreas,
                    optionGroups = optionGroups,
                    sliders = sliders
                )
            )
        }
        features.sortedBy { it.order }
            .mapIndexed { index, feature -> feature.copy(order = index) }
    }
}
