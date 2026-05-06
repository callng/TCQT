package com.owo233.tcqt.activity

import androidx.compose.runtime.Immutable

// ───── Feature Models ─────

@Immutable
data class SettingFeature(
    val key: String,
    val label: String,
    val desc: String,
    val order: Int,
    val tab: String,
    val categoryPath: List<String>,
    val textAreas: List<TextAreaField>,
    val optionGroup: FeatureOptionGroup?
) {
    val expandable: Boolean
        get() = desc.isNotBlank() || textAreas.isNotEmpty() || optionGroup != null

    val labelLower: String by lazy(LazyThreadSafetyMode.NONE) { label.lowercase() }
    val descLower: String by lazy(LazyThreadSafetyMode.NONE) { desc.lowercase() }

    /** Full category path as a display string, e.g. "高级/过检测" */
    val categoryDisplay: String by lazy(LazyThreadSafetyMode.NONE) {
        categoryPath.joinToString(" / ")
    }
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

// ───── Category Navigation Models ─────

/**
 * Represents a category card shown on the current navigation level.
 * A leaf category (depth == maxDepth or children empty) maps to features;
 * an intermediate category maps to sub-categories.
 */
@Immutable
data class CategoryUiState(
    /** Path segment name at this level, e.g. "过检测" */
    val name: String,
    /** Full path from root, e.g. "高级/过检测" */
    val fullPath: String,
    /** Depth: 0 = root level, 1 = first sub-level, … */
    val depth: Int,
    /** Human-readable label (from the main feature's label or same as name) */
    val label: String,
    /** Sorting order */
    val uiOrder: Int,
    /** Feature keys directly under this category (leaf) */
    val featureKeys: List<String>,
    /** Sub-categories (non-leaf) */
    val children: List<CategoryUiState>,
    /** Whether this node is a leaf (has features, no further sub-categories) */
    val isLeaf: Boolean,
    /** Number of enabled features under this entire subtree */
    val enabledCount: Int,
    /** Total feature count under this entire subtree */
    val totalFeatureCount: Int
)

/**
 * A single breadcrumb segment for the top navigation bar.
 */
@Immutable
data class BreadcrumbItem(
    /** Display name, e.g. "高级" */
    val name: String,
    /** Full path that clicking this breadcrumb should navigate to */
    val fullPath: String
)

// ───── Restart Prompt ─────

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
