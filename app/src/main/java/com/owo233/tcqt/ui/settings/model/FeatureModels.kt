package com.owo233.tcqt.ui.settings.model

import androidx.compose.runtime.Immutable
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.ui.settings.FeatureCatalog

@Immutable
data class SettingFeature(
    val key: String,
    val label: String,
    private val staticDesc: String,
    val order: Int,
    val tab: String,
    val categoryPath: List<String>,
    val uiType: ActionUiType,
    val textAreas: List<TextAreaField>,
    val optionGroups: List<FeatureOptionGroup>,
    val sliders: List<FeatureSliderField>
) {

    val desc: String
        get() = FeatureCatalog.getSettingDesc(key, staticDesc)

    val expandable: Boolean
        get() = desc.isNotBlank() || textAreas.isNotEmpty() || optionGroups.isNotEmpty() || sliders.isNotEmpty()

    val labelLower: String
        get() = label.lowercase()

    val descLower: String
        get() = desc.lowercase()
}

@Immutable
data class FeatureItemUiState(
    val key: String,
    val feature: SettingFeature,
    val enabled: Boolean,
    val hasPending: Boolean,
    val optionGroups: List<FeatureOptionGroup>,
    val optionValues: Map<String, Int>,
    val sliders: List<FeatureSliderUiState>,
    val textAreas: List<TextAreaUiState>,
    val uiType: ActionUiType,
    val error: FeatureErrorUiState?,
    val initReady: Boolean
)

@Immutable
data class FeatureErrorUiState(
    val occurredAt: Long,
    val processName: String,
    val stage: String,
    val summary: String,
    val details: String
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
    val title: String,
    val isMulti: Boolean,
    val fallbackValue: Int,
    val options: List<OptionItem>,
    val forcedSelections: Map<Int, List<Int>> = emptyMap()
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

    fun normalizeValue(value: Int): Int {
        if (!isMulti || forcedSelections.isEmpty()) return value

        var normalized = value
        for ((selectedIndex, requiredIndexes) in forcedSelections) {
            val selectedMask = maskAt(selectedIndex) ?: continue
            if ((normalized and selectedMask) == 0) continue

            for (requiredIndex in requiredIndexes) {
                val requiredMask = maskAt(requiredIndex) ?: continue
                normalized = normalized or requiredMask
            }
        }
        return normalized
    }

    private fun maskAt(index: Int): Int? {
        val option = options.getOrNull(index) ?: return null
        return resolveMask(option, index)
    }
}

@Immutable
data class OptionItem(
    val label: String,
    val value: Int
)

@Immutable
data class FeatureSliderField(
    val key: String,
    val label: String,
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val suffix: String = "",
    val defaultValue: Int = 0
)

@Immutable
data class FeatureSliderUiState(
    val key: String,
    val label: String,
    val min: Int,
    val max: Int,
    val step: Int,
    val suffix: String,
    val value: Int
)

// ───── Category Navigation Models ─────

/**
 * Category card at the current navigation level: leaf → features, intermediate → sub-categories.
 */
