package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.dexkit.DexKitCache
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.host.service.AntiRecallConfig
import com.owo233.tcqt.ui.settings.model.CategoryNode
import com.owo233.tcqt.ui.settings.model.CategoryUiState
import com.owo233.tcqt.ui.settings.model.FeatureErrorUiState
import com.owo233.tcqt.ui.settings.model.FeatureItemUiState
import com.owo233.tcqt.ui.settings.model.FeatureOptionGroup
import com.owo233.tcqt.ui.settings.model.FeatureSliderField
import com.owo233.tcqt.ui.settings.model.FeatureSliderUiState
import com.owo233.tcqt.ui.settings.model.SettingFeature
import com.owo233.tcqt.ui.settings.model.TextAreaUiState
import com.owo233.tcqt.ui.settings.model.normalizeSliderValue
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TopAppBarState

class SettingViewModel : ViewModel() {

    private val topBarStates = mutableMapOf<String, TopAppBarState>()

    fun getTopBarState(path: String) = topBarStates.getOrPut(path) {
        TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
    }

    private val scrollStates = mutableMapOf<String, LazyListState>()

    fun getScrollState(path: String): LazyListState {
        return scrollStates.getOrPut(path) { LazyListState() }
    }

    private val definitions = TCQTSetting.settingMap

    private val persistedBooleans = mutableStateMapOf<String, Boolean>()
    private val persistedInts = mutableStateMapOf<String, Int>()
    private val persistedStrings = mutableStateMapOf<String, String>()

    private val pendingBooleans = mutableStateMapOf<String, Boolean>()
    private val pendingInts = mutableStateMapOf<String, Int>()
    private val pendingStrings = mutableStateMapOf<String, String>()

    var detailFeatureKey by mutableStateOf<String?>(null)
        private set

    var isAppearanceActive by mutableStateOf(false)
        private set

    fun openAppearance() {
        isAppearanceActive = true
    }

    val detailFeature: State<FeatureItemUiState?> = derivedStateOf {
        detailFeatureKey?.let(featureByKey::get)?.let(::toFeatureItemUiState)
    }
    private val actionErrors = mutableStateMapOf<String, FeatureErrorUiState>()
    private var errorReloadJob: Job? = null

    // ───── All features (flat) ─────

    private val allFeatures: List<SettingFeature> =
        FeatureCatalog.getAllFeatures()

    private val featureByKey: Map<String, SettingFeature> = allFeatures.associateBy { it.key }

    private val optionGroupByKey: Map<String, FeatureOptionGroup> =
        allFeatures.flatMap { it.optionGroups }.associateBy { it.key }

    private val sliderByKey: Map<String, FeatureSliderField> =
        allFeatures.flatMap { it.sliders }.associateBy { it.key }

    // ───── Category tree ─────

    private val rootNodes: List<CategoryNode> = CategoryTreeBuilder.buildCategoryTree(allFeatures)

    // ───── Navigation ─────

    /** Stack of full paths. Empty = root. */
    private val _navStack = mutableStateListOf<String>()

    /** Current full path; empty at root, e.g. "高级/过检测" at the second level. */
    val currentPath: String
        get() = _navStack.lastOrNull().orEmpty()

    val isAtRoot: Boolean
        get() = _navStack.isEmpty()

    // ───── Search ─────

    var searchQuery by mutableStateOf("")
        private set

    var isSearchActive by mutableStateOf(false)
        private set

    var isErrorOverviewActive by mutableStateOf(false)
        private set

    val searchHistory = mutableStateListOf<String>()

    private val pendingKeywordsToSave = mutableSetOf<String>()

    // ───── Stats (computed dynamically using derivedStateOf) ─────

    val enabledCount: Int by derivedStateOf {
        allFeatures.count { it.uiType == ActionUiType.SWITCH && isFeatureUiEnabled(it.key) }
    }

    val disabledCount: Int by derivedStateOf {
        allFeatures.count { it.uiType == ActionUiType.SWITCH && !isFeatureUiEnabled(it.key) }
    }

    val errorCount: Int by derivedStateOf {
        allFeatures.count { actionErrors.containsKey(it.key) }
    }

    // ───── Pending ─────

    val hasPendingChanges: Boolean
        get() =
            pendingBooleans.isNotEmpty() || pendingInts.isNotEmpty() || pendingStrings.isNotEmpty()

    val pendingChangeCount: Int
        get() = pendingBooleans.size + pendingInts.size + pendingStrings.size

    // ───── Derived state ─────

    /** Categories shown at the current navigation level. */
    val currentCategories: State<List<CategoryUiState>> = derivedStateOf {
        buildCurrentCategories()
    }

    /** Features shown at the current level (only when it is a leaf / has direct features). */
    val currentFeatures: State<List<FeatureItemUiState>> = derivedStateOf {
        computeVisibleFeatureUiStates()
    }

    val errorFeatures: State<List<FeatureItemUiState>> = derivedStateOf {
        allFeatures
            .filter { actionErrors.containsKey(it.key) }
            .sortedByDescending { actionErrors[it.key]?.occurredAt ?: 0L }
            .map(::toFeatureItemUiState)
    }

    /** Page title for the current category level. */
    val currentCategoryLabel: State<String> = derivedStateOf {
        if (isAtRoot) "功能配置"
        else {
            val node = resolveNode(currentPath)
            node?.label?.ifBlank { node.name } ?: currentPath.substringAfterLast('/')
        }
    }

    init {
        AntiRecallConfig.migrateLegacyOptions()
        reloadPersistedSettings()
        reloadActionErrors()
        loadSearchHistory()
    }

    // ───── Navigation ─────

    fun navigateTo(fullPath: String) {
        _navStack.add(fullPath)
    }

    fun navigateUp(): Boolean {
        if (isAppearanceActive) {
            isAppearanceActive = false
            return true
        }
        if (detailFeatureKey != null) {
            detailFeatureKey = null
            return true
        }
        if (isErrorOverviewActive) {
            isErrorOverviewActive = false
            return true
        }
        if (_navStack.isEmpty()) return false
        popNavStack()
        return true
    }

    fun navigateToRoot() {
        isAppearanceActive = false
        detailFeatureKey = null
        isErrorOverviewActive = false
        _navStack.clear()
    }

    fun openErrorOverview() {
        if (errorCount == 0) return
        isSearchActive = false
        searchQuery = ""
        isErrorOverviewActive = true
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

    private fun loadSearchHistory() {
        try {
            val jsonStr = TCQTSetting.getRawString("search_history")
            if (jsonStr.isNotBlank()) {
                val history = Json.decodeFromString<List<String>>(jsonStr)
                searchHistory.clear()
                searchHistory.addAll(history)
            }
        } catch (e: Exception) {
            com.owo233.tcqt.core.log.Log.e("Failed to load search history", e)
        }
    }

    private fun saveSearchHistory() {
        try {
            val jsonStr = Json.encodeToString(searchHistory.toList())
            TCQTSetting.putRawString("search_history", jsonStr)
        } catch (e: Exception) {
            com.owo233.tcqt.core.log.Log.e("Failed to save search history", e)
        }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        searchHistory.remove(trimmed)
        searchHistory.add(0, trimmed)
        while (searchHistory.size > 10) {
            searchHistory.removeAt(searchHistory.lastIndex)
        }
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        saveSearchHistory()
    }

    // ───── Feature interaction ─────

    fun openFeatureDetails(key: String) {
        if (featureByKey.containsKey(key)) detailFeatureKey = key
    }

    fun reloadActionErrors() {
        errorReloadJob?.cancel()
        errorReloadJob = viewModelScope.launch {
            // 文件枚举、跨进程文件锁和 JSON 解码不应阻塞设置页首帧。
            val errors = withContext(Dispatchers.IO) {
                ActionErrorStore.readAll().mapValues { (_, record) ->
                    FeatureErrorUiState(
                        occurredAt = record.occurredAt,
                        processName = record.processName,
                        stage = record.stage,
                        summary = record.summary,
                        details = record.details,
                    )
                }
            }
            actionErrors.clear()
            actionErrors.putAll(errors)
            if (actionErrors.keys.none(featureByKey::containsKey)) {
                isErrorOverviewActive = false
            }
        }
    }

    fun clearActionError(key: String) {
        errorReloadJob?.cancel()
        ActionErrorStore.clear(key)
        actionErrors.remove(key)
        reloadActionErrors()
        if (actionErrors.keys.none(featureByKey::containsKey)) {
            isErrorOverviewActive = false
        }
    }

    fun setFeatureEnabled(key: String, value: Boolean) {
        val feature = featureByKey[key]
        if (feature?.uiType == ActionUiType.ENTRY) return
        if (!FeatureCatalog.isInitReady(key)) return

        val persisted = persistedBooleans[key] ?: false

        if (value == persisted) {
            pendingBooleans.remove(key)
        } else {
            pendingBooleans[key] = value
        }

        if (isSearchActive && searchQuery.isNotBlank()) {
            pendingKeywordsToSave.add(searchQuery.trim())
        }
    }

    fun setOptionValue(key: String, value: Int) {
        val normalizedValue = optionGroupByKey[key]?.normalizeValue(value) ?: value
        val persisted = persistedInts[key] ?: 0
        if (normalizedValue == persisted) {
            pendingInts.remove(key)
        } else {
            pendingInts[key] = normalizedValue
        }

        if (isSearchActive && searchQuery.isNotBlank()) {
            pendingKeywordsToSave.add(searchQuery.trim())
        }
    }

    /** Slider 值按自身 min/max 收敛后进入待保存状态。 */
    fun setSliderValue(key: String, value: Int) {
        val slider = sliderByKey[key]
        val clamped = slider?.let { normalizeSliderValue(value, it.min, it.max, it.step) } ?: value
        val persisted = persistedInts[key] ?: 0
        if (clamped == persisted) pendingInts.remove(key) else pendingInts[key] = clamped

        if (isSearchActive && searchQuery.isNotBlank()) {
            pendingKeywordsToSave.add(searchQuery.trim())
        }
    }

    fun setTextValue(key: String, value: String) {
        val persisted = persistedStrings[key].orEmpty()
        if (value == persisted) {
            pendingStrings.remove(key)
        } else {
            pendingStrings[key] = value
        }

        if (isSearchActive && searchQuery.isNotBlank()) {
            pendingKeywordsToSave.add(searchQuery.trim())
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

        if (pendingKeywordsToSave.isNotEmpty()) {
            pendingKeywordsToSave.forEach { addSearchHistory(it) }
            pendingKeywordsToSave.clear()
        }

        pendingBooleans.clear()
        pendingInts.clear()
        pendingStrings.clear()
    }

    fun clearAllSettings() {
        TCQTSetting.clearAll()
        AntiRecallConfig.migrateLegacyOptions()
        DexKitCache.clearCache()

        pendingBooleans.clear()
        pendingInts.clear()
        pendingStrings.clear()
        topBarStates.clear()
        scrollStates.clear()
        searchHistory.clear()
        pendingKeywordsToSave.clear()

        reloadPersistedSettings()
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
        // No-op: stats are derived state
    }

    // ───── Internal: category building ─────

    private fun buildCurrentCategories(): List<CategoryUiState> {
        val nodes =
            if (isAtRoot) {
                rootNodes
            } else {
                val node = resolveNode(currentPath) ?: return emptyList()
                node.children
            }

        return nodes
            .filter { it.featureKeys.isNotEmpty() || it.children.isNotEmpty() }
            .filterNot { isCollapsibleNode(it) }
            .sortedWith(categoryNodeComparator)
            .map { node -> toCategoryUiState(node) }
    }

    /**
     * A leaf node with exactly one feature carrying the node's own label is redundant: its category
     * page would show a single feature named like the category, so the feature is hoisted to the
     * parent level instead.
     */
    private fun isCollapsibleNode(node: CategoryNode): Boolean {
        if (node.children.isNotEmpty()) return false
        if (node.featureKeys.size != 1) return false
        val featureKey = node.featureKeys.single()
        val feature = featureByKey[featureKey] ?: return false
        return node.label == feature.label
    }

    private fun getCollapsedFeatureKeys(): List<String> {
        val nodes =
            if (isAtRoot) {
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
        val enabledInSubtree = allKeysInSubtree.count { isFeatureUiEnabled(it) }

        return CategoryUiState(
            name = node.name,
            fullPath = node.fullPath,
            depth = node.depth,
            label = node.label.ifBlank { node.name },
            uiOrder = node.uiOrder,
            featureKeys = node.featureKeys,
            children =
                node.children.sortedWith(categoryNodeComparator).map { toCategoryUiState(it) },
            isLeaf = isLeaf,
            enabledCount = enabledInSubtree,
            totalFeatureCount = allKeysInSubtree.size,
        )
    }

    private val categoryNodeComparator =
        compareByDescending<CategoryNode> { collectFeatureKeys(it).size }
            .thenBy { it.uiOrder }
            .thenBy { node -> node.label.ifBlank { node.name } }

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

    // ───── Internal: feature computation ─────

    private fun computeVisibleFeatureUiStates(): List<FeatureItemUiState> {
        val keyword = searchQuery.trim()
        if (isSearchActive && keyword.isBlank()) return emptyList()

        if (keyword.isNotBlank()) {
            // Search mode: flat across all features
            val ranked =
                allFeatures
                    .mapNotNull { feature ->
                        val priority =
                            when {
                                feature.label.equals(keyword, ignoreCase = true) ||
                                    feature.desc.equals(keyword, ignoreCase = true) -> 3
                                feature.label.contains(keyword, ignoreCase = true) -> 2
                                feature.desc.contains(keyword, ignoreCase = true) -> 1
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

        // Normal mode: features whose categoryPath matches the current path,
        // plus features collapsed in from redundant child nodes.
        val collapsedKeys = getCollapsedFeatureKeys()
        val matchPath = currentPath
        val explicitFeatures =
            if (matchPath.isEmpty()) {
                emptyList()
            } else {
                allFeatures.filter { feature ->
                    feature.categoryPath.joinToString("/") == matchPath
                }
            }

        val collapsedFeatures = collapsedKeys.mapNotNull { key -> featureByKey[key] }
        val allVisibleFeatures =
            (explicitFeatures + collapsedFeatures).distinctBy { it.key }.sortedBy { it.order }

        return allVisibleFeatures.map { feature -> toFeatureItemUiState(feature) }
    }

    private fun toFeatureItemUiState(feature: SettingFeature): FeatureItemUiState {
        val initReady = FeatureCatalog.isInitReady(feature.key)
        return FeatureItemUiState(
            key = feature.key,
            feature = feature,
            enabled = initReady && effectiveBoolean(feature.key),
            hasPending = hasPendingFor(feature),
            optionGroups = feature.optionGroups,
            optionValues = feature.optionGroups.associate { it.key to currentOptionValue(it) },
            sliders =
                feature.sliders.map { field ->
                    FeatureSliderUiState(
                        key = field.key,
                        label = field.label,
                        min = field.min,
                        max = field.max,
                        step = field.step,
                        suffix = field.suffix,
                        value =
                            effectiveInt(field.key, field.defaultValue)
                                .coerceIn(field.min, field.max),
                    )
                },
            textAreas =
                feature.textAreas.map { area ->
                    TextAreaUiState(
                        key = area.key,
                        label = area.label,
                        placeholder = area.placeholder,
                        value = effectiveString(area.key),
                    )
                },
            uiType = feature.uiType,
            error = actionErrors[feature.key],
            initReady = initReady,
        )
    }

    // ───── Internal: value helpers ─────

    private fun hasPendingFor(feature: SettingFeature): Boolean {
        if (pendingBooleans.containsKey(feature.key)) return true
        if (feature.optionGroups.any { pendingInts.containsKey(it.key) }) return true
        if (feature.sliders.any { pendingInts.containsKey(it.key) }) return true
        return feature.textAreas.any { pendingStrings.containsKey(it.key) }
    }

    private fun isFeatureUiEnabled(key: String): Boolean {
        return FeatureCatalog.isInitReady(key) && effectiveBoolean(key)
    }

    private fun effectiveBoolean(key: String): Boolean {
        if (featureByKey[key]?.uiType == ActionUiType.ENTRY) return false
        return pendingBooleans[key] ?: persistedBooleans[key] ?: false
    }

    private fun effectiveInt(key: String, fallback: Int = 0): Int {
        return pendingInts[key] ?: persistedInts[key] ?: fallback
    }

    private fun effectiveString(key: String): String {
        return pendingStrings[key] ?: persistedStrings[key].orEmpty()
    }

    private fun currentOptionValue(group: FeatureOptionGroup): Int {
        val baseValue = group.normalizeValue(effectiveInt(group.key, group.fallbackValue))
        return if (group.isMulti) {
            baseValue
        } else {
            group.options.firstOrNull { it.value == baseValue }?.value ?: group.fallbackValue
        }
    }
}

private object CategoryTreeBuilder {

    class TreeNode {

        var pathSegment: String = ""
        var fullPath: String = ""
        var depth: Int = 0
        var label: String = ""
        var uiOrder: Int = 1000
        val children: MutableList<TreeNode> = mutableListOf()
        val featureKeys: MutableList<String> = mutableListOf()
    }

    fun buildCategoryTree(features: List<SettingFeature>): List<CategoryNode> {
        val root = TreeNode()

        for (feat in features) {
            var current = root
            for (i in feat.categoryPath.indices) {
                val segment = feat.categoryPath[i]
                val isLast = i == feat.categoryPath.lastIndex
                val existing = current.children.find { it.pathSegment == segment }
                val node =
                    existing
                        ?: TreeNode()
                            .apply {
                                pathSegment = segment
                                fullPath =
                                    if (current == root) segment else "${current.fullPath}/$segment"
                                depth = i
                                label = segment
                            }
                            .also { current.children.add(it) }
                if (isLast) {
                    node.featureKeys.add(feat.key)
                    node.uiOrder = feat.order
                }
                current = node
            }
        }

        fun sortTree(node: TreeNode) {
            node.children.sortWith(compareBy<TreeNode> { it.depth }.thenBy { it.uiOrder })
            node.children.forEach { sortTree(it) }
        }
        sortTree(root)

        fun toCategoryNode(node: TreeNode): CategoryNode {
            return CategoryNode(
                name = node.pathSegment,
                fullPath = node.fullPath,
                depth = node.depth,
                label = node.label,
                uiOrder = node.uiOrder,
                featureKeys = node.featureKeys,
                children = node.children.map { toCategoryNode(it) },
            )
        }

        return root.children.map { toCategoryNode(it) }
    }
}
