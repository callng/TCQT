package com.owo233.tcqt.activity

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.owo233.tcqt.ext.ActionUiType
import com.owo233.tcqt.hooks.helper.AntiRecallConfig
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.dexkit.DexKitCache
import kotlinx.serialization.json.Json

class SettingViewModel : ViewModel() {

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

    private val expandedKeys = mutableStateMapOf<String, Boolean>()

    // ───── All features (flat) ─────

    private val allFeatures: List<SettingFeature> = com.owo233.tcqt.ActionManager.getAllFeatures()
        .sortedBy { it.order }
        .mapIndexed { index, feature -> feature.copy(order = index) }

    private val featureByKey: Map<String, SettingFeature> = allFeatures.associateBy { it.key }

    private val optionGroupByKey: Map<String, FeatureOptionGroup> = allFeatures
        .mapNotNull { it.optionGroup }
        .associateBy { it.key }

    // ───── Category tree ─────

    private val rootNodes: List<CategoryNode> = CategoryTreeBuilder.buildCategoryTree(allFeatures)

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

    val searchHistory = mutableStateListOf<String>()

    private val pendingKeywordsToSave = mutableSetOf<String>()

    // ───── Stats (computed dynamically using derivedStateOf) ─────

    val enabledCount: Int by derivedStateOf {
        allFeatures.count { it.uiType == ActionUiType.SWITCH && effectiveBoolean(it.key) }
    }

    val disabledCount: Int by derivedStateOf {
        allFeatures.count { it.uiType == ActionUiType.SWITCH && !effectiveBoolean(it.key) }
    }

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
        AntiRecallConfig.migrateLegacyOptions()
        reloadPersistedSettings()
        loadSearchHistory()
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
        val idx = _navStack.indexOf(fullPath)
        if (idx >= 0) {
            while (_navStack.size > idx + 1) {
                _navStack.removeAt(_navStack.lastIndex)
            }
        } else {
            _navStack.clear()
            _navStack.add(fullPath)
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

    private fun loadSearchHistory() {
        try {
            val jsonStr = TCQTSetting.getRawString("search_history")
            if (jsonStr.isNotBlank()) {
                val history = Json.decodeFromString<List<String>>(jsonStr)
                searchHistory.clear()
                searchHistory.addAll(history)
            }
        } catch (e: Exception) {
            com.owo233.tcqt.utils.log.Log.e("Failed to load search history", e)
        }
    }

    private fun saveSearchHistory() {
        try {
            val jsonStr = Json.encodeToString(searchHistory.toList())
            TCQTSetting.putRawString("search_history", jsonStr)
        } catch (e: Exception) {
            com.owo233.tcqt.utils.log.Log.e("Failed to save search history", e)
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

    fun toggleExpanded(key: String) {
        expandedKeys[key] = !(expandedKeys[key] ?: false)
    }

    fun setFeatureEnabled(key: String, value: Boolean) {
        val feature = featureByKey[key]
        if (feature?.uiType == ActionUiType.ENTRY) return

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
        expandedKeys.clear()
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
        // No-op: statistics are automatically computed using derivedStateOf
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
            .sortedWith(categoryNodeComparator)
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
            children = node.children.sortedWith(categoryNodeComparator).map { toCategoryUiState(it) },
            isLeaf = isLeaf,
            enabledCount = enabledInSubtree,
            totalFeatureCount = allKeysInSubtree.size
        )
    }

    private val categoryNodeComparator = compareByDescending<CategoryNode> { collectFeatureKeys(it).size }
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
            },
            uiType = feature.uiType
        )
    }

    // ───── Internal: value helpers ─────

    private fun hasPendingFor(feature: SettingFeature): Boolean {
        if (pendingBooleans.containsKey(feature.key)) return true
        if (feature.optionGroup != null && pendingInts.containsKey(feature.optionGroup.key)) return true
        return feature.textAreas.any { pendingStrings.containsKey(it.key) }
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
                val node = existing
                    ?: TreeNode().apply {
                        pathSegment = segment
                        fullPath = if (current == root) segment else "${current.fullPath}/$segment"
                        depth = i
                        label = segment
                    }.also { current.children.add(it) }
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
                children = node.children.map { toCategoryNode(it) }
            )
        }

        return root.children.map { toCategoryNode(it) }
    }
}
