package com.owo233.tcqt.activity

import android.annotation.SuppressLint
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.owo233.tcqt.generated.GeneratedCategoryTree
import com.owo233.tcqt.generated.GeneratedCategoryTree.CategoryNode
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

    // ───── All features (flat) ─────

    private val allFeatures: List<SettingFeature> = GeneratedFeaturesData.FEATURES
        .sortedBy { it.uiOrder }
        .mapIndexed { index, feature -> feature.toSettingFeature(index) }

    private val featureByKey: Map<String, SettingFeature> = allFeatures.associateBy { it.key }

    // ───── Category tree ─────

    private val rootNodes: List<CategoryNode> = GeneratedCategoryTree.ROOTS

    // ───── Navigation ─────

    /** Stack of full paths. Empty = root. */
    private val _navStack = mutableStateListOf<String>()

    /** Current path, e.g. "" for root, "高级" for first level, "高级/过检测" for second. */
    val currentPath: String
        get() = _navStack.lastOrNull().orEmpty()

    val isAtRoot: Boolean
        get() = _navStack.isEmpty()

    // ───── Search ─────

    var searchQuery by mutableStateOf("")
        private set

    var isSearchActive by mutableStateOf(false)
        private set

    // ───── Stats (scoped to current level) ─────

    var enabledCount by mutableIntStateOf(0)
        private set

    var disabledCount by mutableIntStateOf(0)
        private set

    // ───── Pending ─────

    val hasPendingChanges: Boolean
        get() = pendingBooleans.isNotEmpty() || pendingInts.isNotEmpty() || pendingStrings.isNotEmpty()

    val pendingChangeCount: Int
        get() = pendingBooleans.size + pendingInts.size + pendingStrings.size

    // ───── Derived state ─────

    /** Categories shown at the current navigation level. */
    val currentCategories: State<List<CategoryUiState>> = derivedStateOf {
        buildCurrentCategories()
    }

    /** Features shown at the current level (only when it's a leaf / has direct features). */
    val currentFeatures: State<List<FeatureItemUiState>> = derivedStateOf {
        computeVisibleFeatureUiStates()
    }

    /** Breadcrumb trail for the top bar. */
    val breadcrumbs: State<List<BreadcrumbItem>> = derivedStateOf {
        buildBreadcrumbs()
    }

    /** Label for the current category level (used as page title). */
    val currentCategoryLabel: State<String> = derivedStateOf {
        if (isAtRoot) "功能配置"
        else {
            val node = resolveNode(currentPath)
            node?.label?.ifBlank { node.name } ?: currentPath.substringAfterLast('/')
        }
    }

    init {
        reloadPersistedSettings()
        recalculateStats()
    }

    // ───── Navigation ─────

    fun navigateTo(fullPath: String) {
        _navStack.add(fullPath)
    }

    fun navigateUp(): Boolean {
        if (_navStack.isEmpty()) return false
        popNavStack()
        return true
    }

    fun navigateToRoot() {
        _navStack.clear()
    }

    fun navigateToBreadcrumb(fullPath: String) {
        if (fullPath.isEmpty()) {
            navigateToRoot()
            return
        }
        // Pop back to the point where we can push this path
        while (_navStack.isNotEmpty() && _navStack.last() != fullPath) {
            val last = _navStack.last()
            if (fullPath.startsWith(last) && _navStack.size > 1) {
                // Check if the previous element is a prefix
                val prev = _navStack[_navStack.size - 2]
                if (fullPath.startsWith(prev)) {
                    popNavStack()
                    continue
                }
            }
            popNavStack()
        }
        if (_navStack.isEmpty() || _navStack.last() != fullPath) {
            _navStack.clear()
            if (fullPath.isNotEmpty()) {
                _navStack.add(fullPath)
            }
        }
    }

    private fun popNavStack(): String = _navStack.removeAt(_navStack.lastIndex)

    // ───── Search ─────

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

    // ───── Feature interaction ─────

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

    // ───── Internal: category building ─────

    private fun buildCurrentCategories(): List<CategoryUiState> {
        val nodes = if (isAtRoot) {
            rootNodes
        } else {
            val node = resolveNode(currentPath) ?: return emptyList()
            node.children
        }

        return nodes
            .filter { it.featureKeys.isNotEmpty() || it.children.isNotEmpty() }
            .filterNot { isCollapsibleNode(it) }
            .map { node -> toCategoryUiState(node) }
    }

    /**
     * A leaf node with exactly one feature whose label matches the node's own
     * label is redundant — the category page would just show a single feature
     * with the same name.  Collapse it so the feature appears at the parent level.
     */
    private fun isCollapsibleNode(node: CategoryNode): Boolean {
        if (node.children.isNotEmpty()) return false
        if (node.featureKeys.size != 1) return false
        val featureKey = node.featureKeys.single()
        val feature = featureByKey[featureKey] ?: return false
        return node.label == feature.label
    }

    private fun getCollapsedFeatureKeys(): List<String> {
        val nodes = if (isAtRoot) {
            rootNodes
        } else {
            val node = resolveNode(currentPath) ?: return emptyList()
            node.children
        }
        return nodes.filter { isCollapsibleNode(it) }.flatMap { it.featureKeys }
    }

    private fun toCategoryUiState(node: CategoryNode): CategoryUiState {
        val isLeaf = node.children.isEmpty()
        val allKeysInSubtree = collectFeatureKeys(node)
        val enabledInSubtree = allKeysInSubtree.count { effectiveBoolean(it) }

        return CategoryUiState(
            name = node.name,
            fullPath = node.fullPath,
            depth = node.depth,
            label = node.label.ifBlank { node.name },
            uiOrder = node.uiOrder,
            featureKeys = node.featureKeys,
            children = node.children.map { toCategoryUiState(it) },
            isLeaf = isLeaf,
            enabledCount = enabledInSubtree,
            totalFeatureCount = allKeysInSubtree.size
        )
    }

    private fun collectFeatureKeys(node: CategoryNode): List<String> {
        val result = mutableListOf<String>()
        result.addAll(node.featureKeys)
        node.children.forEach { result.addAll(collectFeatureKeys(it)) }
        return result
    }

    private fun resolveNode(fullPath: String): CategoryNode? {
        if (fullPath.isEmpty()) return null

        val segments = fullPath.split("/")
        var currentList = rootNodes
        for (segment in segments) {
            val node = currentList.find { it.name == segment } ?: return null
            if (segment == segments.last()) return node
            currentList = node.children
        }
        return null
    }

    private fun buildBreadcrumbs(): List<BreadcrumbItem> {
        if (_navStack.isEmpty()) return emptyList()

        val result = mutableListOf<BreadcrumbItem>()
        for (path in _navStack) {
            val node = resolveNode(path)
            val name = node?.name ?: path.substringAfterLast('/')
            result.add(BreadcrumbItem(name = name, fullPath = path))
        }
        return result
    }

    // ───── Internal: feature computation ─────

    private fun computeVisibleFeatureUiStates(): List<FeatureItemUiState> {
        val keyword = searchQuery.trim().lowercase()

        if (keyword.isNotBlank()) {
            // Search mode: flat across all features
            val ranked = allFeatures
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

            return ranked.map { feature -> toFeatureItemUiState(feature) }
        }

        // Normal mode: features whose categoryPath exactly matches current path,
        // plus collapsed features from child nodes that are redundant.
        val collapsedKeys = getCollapsedFeatureKeys()
        val matchPath = currentPath
        val explicitFeatures = if (matchPath.isEmpty()) {
            emptyList()
        } else {
            allFeatures.filter { feature ->
                feature.categoryPath.joinToString("/") == matchPath
            }
        }

        val collapsedFeatures = collapsedKeys.mapNotNull { key -> featureByKey[key] }
        val allVisibleFeatures = (explicitFeatures + collapsedFeatures)
            .distinctBy { it.key }
            .sortedBy { it.order }

        return allVisibleFeatures.map { feature -> toFeatureItemUiState(feature) }
    }

    private fun toFeatureItemUiState(feature: SettingFeature): FeatureItemUiState {
        return FeatureItemUiState(
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

    // ───── Internal: value helpers ─────

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

    // ───── Internal: conversion ─────

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
            categoryPath = categoryPath.toList(),
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
