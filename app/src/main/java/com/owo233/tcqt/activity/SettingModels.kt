package com.owo233.tcqt.activity

import androidx.compose.runtime.Immutable

@Immutable
data class SettingFeature(
    val key: String,
    val label: String,
    val desc: String,
    val order: Int,
    val tab: String,
    val textAreas: List<TextAreaField>,
    val optionGroup: FeatureOptionGroup?
) {
    val expandable: Boolean
        get() = desc.isNotBlank() || textAreas.isNotEmpty() || optionGroup != null

    val labelLower: String by lazy(LazyThreadSafetyMode.NONE) { label.lowercase() }
    val descLower: String by lazy(LazyThreadSafetyMode.NONE) { desc.lowercase() }
}

@Immutable
data class FeatureItemUiState(
    val key: String,
    val feature: SettingFeature,
    val enabled: Boolean,
    val expanded: Boolean,
    val hasPending: Boolean,
    val optionGroup: FeatureOptionGroup?,
    val optionValue: Int?,
    val textAreas: List<TextAreaUiState>
)

@Immutable
data class TextAreaUiState(
    val key: String,
    val label: String,
    val placeholder: String,
    val value: String
)

@Immutable
data class TextAreaField(
    val key: String,
    val label: String,
    val placeholder: String
)

@Immutable
data class FeatureOptionGroup(
    val key: String,
    val isMulti: Boolean,
    val fallbackValue: Int,
    val options: List<OptionItem>
) {
    private val useOptionValueAsMask: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        isMulti && options.all { option ->
            option.value > 0 && (option.value and (option.value - 1)) == 0
        }
    }

    fun resolveMask(option: OptionItem, index: Int): Int {
        if (!isMulti) return option.value
        return if (useOptionValueAsMask) option.value else (1 shl index)
    }
}

@Immutable
data class OptionItem(
    val label: String,
    val value: Int
)

enum class RestartPrompt(
    val title: String,
    val message: String,
    val dismissMessage: String
) {
    Save(
        title = "设置已保存",
        message = "是否现在重启宿主以应用修改？",
        dismissMessage = "设置已保存，重启后生效"
    ),
    Clear(
        title = "配置已清空",
        message = "是否现在重启宿主以应用默认配置？",
        dismissMessage = "配置已清空，重启后生效"
    ),
    Restore(
        title = "配置已还原",
        message = "是否现在重启宿主以应用还原的配置？",
        dismissMessage = "配置已还原，重启后生效"
    )
}
