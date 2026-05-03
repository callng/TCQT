package com.owo233.tcqt.activity

import android.annotation.SuppressLint
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.owo233.tcqt.generated.GeneratedFeaturesData
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.dexkit.DexKitCache

@SuppressLint("AutoboxingStateCreation")
class SettingViewModel : ViewModel() {

    private val definitions = GeneratedSettingList.SETTING_MAP

    private val persistedBooleans = mutableStateMapOf<String, Boolean>()
    private val persistedInts = mutableStateMapOf<String, Int>()
    private val persistedStrings = mutableStateMapOf<String, String>()

    private val pendingBooleans = mutableStateMapOf<String, Boolean>()
    private val pendingInts = mutableStateMapOf<String, Int>()
    private val pendingStrings = mutableStateMapOf<String, String>()

    private val expandedKeys = mutableStateMapOf<String, Boolean>()

    private val allFeatures = GeneratedFeaturesData.FEATURES
        .sortedBy { it.uiOrder }
        .mapIndexed { index, feature -> feature.toSettingFeature(index) }

    val tabs: List<String> = allFeatures
        .map { it.tab }
        .distinct()
        .sortedWith(compareBy<String> { tabPriority(it) }.thenBy { it })

    var currentTab by mutableStateOf(tabs.firstOrNull().orEmpty())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var isSearchActive by mutableStateOf(false)
        private set

    var enabledCount by mutableIntStateOf(0)
        private set

    var disabledCount by mutableIntStateOf(0)
        private set

    val hasPendingChanges: Boolean
        get() = pendingBooleans.isNotEmpty() || pendingInts.isNotEmpty() || pendingStrings.isNotEmpty()

    val pendingChangeCount: Int
        get() = pendingBooleans.size + pendingInts.size + pendingStrings.size

    val visibleFeaturesState: State<List<FeatureItemUiState>> = derivedStateOf {
        computeVisibleFeatureUiStates()
    }

    init {
        reloadPersistedSettings()
        recalculateStats()
    }

    private fun computeVisibleFeatureUiStates(): List<FeatureItemUiState> {
        val keyword = searchQuery.trim().lowercase()

        val visibleFeatures = if (keyword.isBlank()) {
            if (currentTab.isBlank()) {
                allFeatures
            } else {
                allFeatures.filter { it.tab == currentTab }
            }
        } else {
            allFeatures
                .mapNotNull { feature ->
                    val priority = when {
                        feature.labelLower == keyword || feature.descLower == keyword -> 3
                        feature.labelLower.contains(keyword) -> 2
                        feature.descLower.contains(keyword) -> 1
                        else -> 0
                    }
                    if (priority == 0) null else priority to feature
                }
                .sortedWith(
                    compareByDescending<Pair<Int, SettingFeature>> { it.first }
                        .thenBy { it.second.order }
                )
                .map { it.second }
        }

        return visibleFeatures.map { feature ->
            FeatureItemUiState(
                key = feature.key,
                feature = feature,
                enabled = effectiveBoolean(feature.key),
                expanded = expandedKeys[feature.key] == true,
                hasPending = hasPendingFor(feature),
                optionGroup = feature.optionGroup,
                optionValue = feature.optionGroup?.let(::currentOptionValue),
                textAreas = feature.textAreas.map { area ->
                    TextAreaUiState(
                        key = area.key,
                        label = area.label,
                        placeholder = area.placeholder,
                        value = effectiveString(area.key)
                    )
                }
            )
        }
    }

    fun enterSearch() {
        isSearchActive = true
    }

    fun exitSearch() {
        isSearchActive = false
        searchQuery = ""
    }

    fun updateSearchQuery(value: String) {
        searchQuery = value
    }

    fun clearSearchQuery() {
        searchQuery = ""
    }

    fun selectTab(tab: String) {
        currentTab = tab
        if (isSearchActive) {
            exitSearch()
        }
    }

    fun toggleExpanded(key: String) {
        expandedKeys[key] = !(expandedKeys[key] ?: false)
    }

    fun setFeatureEnabled(key: String, value: Boolean) {
        val before = effectiveBoolean(key)
        val persisted = persistedBooleans[key] ?: false

        if (value == persisted) {
            pendingBooleans.remove(key)
        } else {
            pendingBooleans[key] = value
        }

        val after = effectiveBoolean(key)
        if (before != after) {
            enabledCount += if (after) 1 else -1
            disabledCount = (allFeatures.size - enabledCount).coerceAtLeast(0)
        }
    }

    fun setOptionValue(key: String, value: Int) {
        val persisted = persistedInts[key] ?: 0
        if (value == persisted) {
            pendingInts.remove(key)
        } else {
            pendingInts[key] = value
        }
    }

    fun setTextValue(key: String, value: String) {
        val persisted = persistedStrings[key].orEmpty()
        if (value == persisted) {
            pendingStrings.remove(key)
        } else {
            pendingStrings[key] = value
        }
    }

    fun saveChanges() {
        pendingBooleans.forEach { (key, value) ->
            TCQTSetting.setValue(key, value)
            persistedBooleans[key] = value
        }
        pendingInts.forEach { (key, value) ->
            TCQTSetting.setValue(key, value)
            persistedInts[key] = value
        }
        pendingStrings.forEach { (key, value) ->
            TCQTSetting.setValue(key, value)
            persistedStrings[key] = value
        }

        pendingBooleans.clear()
        pendingInts.clear()
        pendingStrings.clear()
        recalculateStats()
    }

    fun clearAllSettings() {
        TCQTSetting.clearAll()
        DexKitCache.clearCache()

        pendingBooleans.clear()
        pendingInts.clear()
        pendingStrings.clear()
        expandedKeys.clear()

        reloadPersistedSettings()
        recalculateStats()
    }

    fun reloadPersistedSettings() {
        persistedBooleans.clear()
        persistedInts.clear()
        persistedStrings.clear()

        definitions.forEach { (key, setting) ->
            when (setting.type) {
                TCQTSetting.SettingType.BOOLEAN -> {
                    persistedBooleans[key] = TCQTSetting.getValue<Boolean>(key) ?: false
                }

                TCQTSetting.SettingType.INT,
                TCQTSetting.SettingType.INT_MULTI -> {
                    persistedInts[key] = TCQTSetting.getValue<Int>(key) ?: 0
                }

                TCQTSetting.SettingType.STRING -> {
                    persistedStrings[key] = TCQTSetting.getValue<String>(key).orEmpty()
                }
            }
        }
    }

    fun recalculateStats() {
        val enabled = allFeatures.count { feature ->
            effectiveBoolean(feature.key)
        }
        enabledCount = enabled
        disabledCount = (allFeatures.size - enabled).coerceAtLeast(0)
    }

    private fun hasPendingFor(feature: SettingFeature): Boolean {
        if (pendingBooleans.containsKey(feature.key)) return true
        if (feature.optionGroup != null && pendingInts.containsKey(feature.optionGroup.key)) return true
        return feature.textAreas.any { pendingStrings.containsKey(it.key) }
    }

    private fun effectiveBoolean(key: String): Boolean {
        return pendingBooleans[key] ?: persistedBooleans[key] ?: false
    }

    private fun effectiveInt(key: String, fallback: Int = 0): Int {
        return pendingInts[key] ?: persistedInts[key] ?: fallback
    }

    private fun effectiveString(key: String): String {
        return pendingStrings[key] ?: persistedStrings[key].orEmpty()
    }

    private fun currentOptionValue(group: FeatureOptionGroup): Int {
        val baseValue = effectiveInt(group.key, group.fallbackValue)
        return if (group.isMulti) {
            baseValue
        } else {
            group.options.firstOrNull { it.value == baseValue }?.value ?: group.fallbackValue
        }
    }

    private fun tabPriority(tab: String): Int {
        return when (tab) {
            "基础" -> 0
            "杂项" -> 100
            "调试" -> 101
            else -> 1
        }
    }

    private fun GeneratedFeaturesData.FeatureConfig.toSettingFeature(order: Int): SettingFeature {
        val optionGroup = options
            ?.takeIf { it.isNotEmpty() }
            ?.let { optionList ->
                FeatureOptionGroup(
                    key = optionList.first().key,
                    isMulti = optionList.first().isMulti,
                    fallbackValue = if (optionList.first().isMulti) 0 else optionList.first().value,
                    options = optionList.map { option ->
                        OptionItem(
                            label = option.label,
                            value = option.value
                        )
                    }
                )
            }

        return SettingFeature(
            key = key,
            label = label,
            desc = desc,
            order = order,
            tab = uiTab.ifBlank { "基础" },
            textAreas = textareas.orEmpty().map { textArea ->
                TextAreaField(
                    key = textArea.key,
                    label = textArea.key.substringAfterLast('.'),
                    placeholder = textArea.placeholder
                )
            },
            optionGroup = optionGroup
        )
    }
}
