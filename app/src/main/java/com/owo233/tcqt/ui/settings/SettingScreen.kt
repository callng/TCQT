package com.owo233.tcqt.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.PreferenceSegment
import com.owo233.tcqt.ui.component.horizontalSafePadding
import com.owo233.tcqt.ui.settings.model.CategoryUiState
import com.owo233.tcqt.ui.settings.model.FeatureItemUiState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

internal val SettingsRowPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
private const val PageTransitionDurationMillis = 220

internal fun softFadeScaleIn(initialScale: Float, durationMillis: Int) =
    fadeIn(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
        scaleIn(initialScale = initialScale, animationSpec = tween(durationMillis))

internal fun softFadeScaleOut(targetScale: Float, durationMillis: Int) =
    fadeOut(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
        scaleOut(targetScale = targetScale, animationSpec = tween(durationMillis))

private data class SettingsPageContentState(
    val path: String,
    val isAtRoot: Boolean,
    val isSearchActive: Boolean,
    val isErrorOverviewActive: Boolean,
    val isAppearanceActive: Boolean,
    val detail: FeatureItemUiState?,
    val searchQuery: String,
    val categories: List<CategoryUiState>,
    val features: List<FeatureItemUiState>,
    val errorCount: Int,
) {
    val animationKey: String
        get() = when {
            detail != null -> "detail:${detail.key}"
            isAppearanceActive -> "appearance"
            isSearchActive -> "search"
            isErrorOverviewActive -> "errors"
            else -> "category:$path"
        }
}

@Composable
internal fun SettingScreen(
    viewModel: SettingViewModel,
    snackbarHostState: SnackbarHostState,
    themeMode: ModuleThemeMode,
    monetEnabled: Boolean,
    onThemeModeChange: (ModuleThemeMode) -> Unit,
    onMonetEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackupRestoreClick: () -> Unit,
    onSelectPngIcon: (String) -> Unit,
    isExportingBugReport: Boolean,
    onExportBugReportClick: () -> Unit,
    onFeatureClick: (String) -> Unit,
) {
    val categories by viewModel.currentCategories
    val features by viewModel.currentFeatures
    val errorFeatures by viewModel.errorFeatures
    val detail by viewModel.detailFeature
    val isAppearanceActive = viewModel.isAppearanceActive
    val isSearchActive = viewModel.isSearchActive && detail == null && !isAppearanceActive
    val isErrorOverviewActive = viewModel.isErrorOverviewActive && detail == null
    val pageState = SettingsPageContentState(
        path = viewModel.currentPath,
        isAtRoot = viewModel.isAtRoot,
        isSearchActive = isSearchActive,
        isErrorOverviewActive = isErrorOverviewActive,
        isAppearanceActive = isAppearanceActive,
        detail = detail,
        searchQuery = viewModel.searchQuery,
        categories = categories,
        features = if (isErrorOverviewActive) errorFeatures else features,
        errorCount = viewModel.errorCount,
    )
    // The fixed search field has no collapsing app bar to measure the scroll range.
    // Attaching its unmeasured behavior would consume upward drags indefinitely.
    val scrollBehavior = if (isSearchActive) null else {
        MiuixScrollBehavior(viewModel.getTopBarState(pageState.animationKey))
    }
    val title = when {
        detail != null -> detail!!.feature.label
        isAppearanceActive -> "主题设置"
        isErrorOverviewActive -> "运行异常"
        viewModel.isAtRoot -> "设置"
        else -> viewModel.currentCategoryLabel.value
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopBar(
                title = title,
                showBack = true,
                isSearchActive = isSearchActive,
                searchQuery = viewModel.searchQuery,
                onBack = onBack,
                onSearchRequested = if (detail == null && !isAppearanceActive) onSearchRequested else null,
                onSearchClosed = onSearchClosed,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isErrorOverviewActive = isErrorOverviewActive,
                isExportingBugReport = isExportingBugReport,
                onExportBugReportClick = onExportBugReportClick,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState, modifier = Modifier.padding(horizontal = 16.dp))
        },
        bottomBar = {
            if (viewModel.hasPendingChanges) {
                Box(Modifier.background(MiuixTheme.colorScheme.surface).navigationBarsPadding()) {
                    SaveChangesBar(pendingCount = viewModel.pendingChangeCount, onClick = onSaveClick)
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.animationKey },
            transitionSpec = {
                softFadeScaleIn(0.985f, PageTransitionDurationMillis)
                    .togetherWith(softFadeScaleOut(1f, PageTransitionDurationMillis))
            },
            label = "settings_page_transition",
        ) { targetPage ->
            val listState = remember(targetPage.animationKey) {
                viewModel.getScrollState(targetPage.animationKey)
            }
            // Only the active page may drive the currently displayed app bar during transitions.
            val listScrollModifier = if (scrollBehavior != null && targetPage.animationKey == pageState.animationKey) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else Modifier
            AdaptiveContent { sidePadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().horizontalSafePadding()
                        .then(listScrollModifier).overScrollVertical(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        end = sidePadding,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    when {
                        targetPage.detail != null -> featureDetail(targetPage.detail, viewModel, onFeatureClick, onSelectPngIcon)
                        targetPage.isAppearanceActive -> {
                            settingsGroupTitle("外观")
                            item(key = "theme_mode") {
                                PreferenceSegment(first = true, last = false) {
                                    OverlayDropdownPreference(
                                        title = "主题模式",
                                        items = listOf("跟随系统", "浅色模式", "深色模式"),
                                        selectedIndex = ModuleThemeMode.entries.indexOf(themeMode),
                                        insideMargin = SettingsRowPadding,
                                        onSelectedIndexChange = { onThemeModeChange(ModuleThemeMode.entries[it]) },
                                    )
                                }
                            }
                            item(key = "monet") {
                                PreferenceSegment(first = false, last = true) {
                                    SwitchPreference(
                                        title = "动态颜色",
                                        summary = "使用系统壁纸配色",
                                        checked = monetEnabled,
                                        onCheckedChange = onMonetEnabledChange,
                                        insideMargin = SettingsRowPadding,
                                    )
                                }
                            }
                        }
                        else -> featureDirectory(
                            page = targetPage,
                            viewModel = viewModel,
                            onFeatureClick = onFeatureClick,
                            onBackupRestoreClick = onBackupRestoreClick,
                            onIssueClick = onIssueClick,
                            onIssueLongClick = onIssueLongClick,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.settingsGroupTitle(title: String) {
    item(key = "group:$title") {
        SmallTitle(text = title, insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp))
    }
}

private fun LazyListScope.featureDetail(
    item: FeatureItemUiState,
    viewModel: SettingViewModel,
    onFeatureClick: (String) -> Unit,
    onSelectPngIcon: (String) -> Unit,
) {
    item(key = "feature_enable:${item.key}") {
        PreferenceSegment(first = true, last = true) {
            if (item.uiType == ActionUiType.ENTRY) {
                ArrowPreference(
                    title = item.feature.label,
                    summary = item.feature.desc.takeIf(String::isNotBlank),
                    insideMargin = SettingsRowPadding,
                    enabled = item.initReady,
                    onClick = { onFeatureClick(item.key) },
                )
            } else {
                SwitchPreference(
                    title = "启用功能",
                    summary = if (item.initReady) item.feature.desc.takeIf(String::isNotBlank) else "当前宿主不支持此功能",
                    checked = item.enabled,
                    enabled = item.initReady,
                    onCheckedChange = { viewModel.setFeatureEnabled(item.key, it) },
                    insideMargin = SettingsRowPadding,
                )
            }
        }
    }
    if (item.initReady) {
        val fieldCount = item.optionGroups.size + item.sliders.size + item.textAreas.size
        if (fieldCount > 0) settingsGroupTitle("选项")
        itemsIndexed(item.optionGroups, key = { _, group -> "option:${group.key}" }) { index, group ->
            PreferenceSegment(first = index == 0, last = index == fieldCount - 1) {
                OptionGroup(group, item.optionValues[group.key] ?: group.fallbackValue) {
                    viewModel.setOptionValue(group.key, it)
                }
            }
        }
        itemsIndexed(item.sliders, key = { _, slider -> "slider:${slider.key}" }) { index, slider ->
            val position = item.optionGroups.size + index
            PreferenceSegment(first = position == 0, last = position == fieldCount - 1) {
                FeatureSlider(slider) { viewModel.setSliderValue(slider.key, it) }
            }
        }
        itemsIndexed(item.textAreas, key = { _, area -> "text:${area.key}" }) { index, area ->
            val position = item.optionGroups.size + item.sliders.size + index
            PreferenceSegment(first = position == 0, last = position == fieldCount - 1) {
                if (area.isPngIcon) {
                    PngIconPreference(
                        area = area,
                        importing = viewModel.isImportingPngIcon,
                        onSelect = { onSelectPngIcon(area.key) },
                        onReset = { viewModel.setTextValue(area.key, "") },
                    )
                } else {
                    Box(Modifier.padding(16.dp)) {
                        FeatureTextArea(area, repositionKey = Unit) { viewModel.setTextValue(area.key, it) }
                    }
                }
            }
        }
    }
    item.error?.let { error ->
        settingsGroupTitle("诊断")
        item(key = "error:${item.key}") {
            FeatureErrorPanel(error) { viewModel.clearActionError(item.key) }
        }
    }
}

private fun LazyListScope.featureDirectory(
    page: SettingsPageContentState,
    viewModel: SettingViewModel,
    onFeatureClick: (String) -> Unit,
    onBackupRestoreClick: () -> Unit,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
) {
    val isRoot = page.isAtRoot && !page.isSearchActive && !page.isErrorOverviewActive
    if (isRoot) {
        item(key = "host") {
            CompactHeaderCard(
                hostName = HookEnv.appName,
                hostVersion = "${HookEnv.versionName} (${HookEnv.versionCode}) ${PlatformTools.getHostChannel()}",
                moduleName = TCQTBuild.APP_NAME,
                moduleVersion = "${TCQTBuild.VER_NAME} ${if (TCQTBuild.DEBUG) "D" else "R"}",
                errorCount = page.errorCount,
                onErrorClick = viewModel::openErrorOverview,
            )
        }
    }
    if (page.isErrorOverviewActive) {
        item(key = "error_overview_intro") { ErrorOverviewHeader(page.errorCount) }
        settingsGroupTitle("异常功能")
    }
    if (page.isSearchActive) {
        if (page.searchQuery.isBlank()) {
            item(key = "search_history") {
                if (viewModel.searchHistory.isEmpty()) SearchPromptCard()
                else SearchHistoryLayout(
                    history = viewModel.searchHistory,
                    onTagClick = viewModel::updateSearchQuery,
                    onClearClick = viewModel::clearSearchHistory,
                )
            }
        } else if (page.features.isEmpty()) {
            item(key = "search_empty") {
                EmptyStateCard(MiuixIcons.Search, "未找到相关功能", "换一个关键词试试")
            }
        } else settingsGroupTitle("搜索结果 · ${page.features.size}")
    } else if (!page.isErrorOverviewActive && page.categories.isNotEmpty()) {
        settingsGroupTitle(if (isRoot) "功能" else "分类")
        itemsIndexed(page.categories, key = { _, category -> "category:${category.fullPath}" }) { index, category ->
            PreferenceSegment(first = index == 0, last = index == page.categories.lastIndex) {
                CategoryCard(category) { viewModel.navigateTo(category.fullPath) }
            }
        }
    }
    if (page.features.isNotEmpty()) {
        if (!page.isSearchActive && !page.isErrorOverviewActive && page.categories.isNotEmpty()) settingsGroupTitle("其他功能")
        itemsIndexed(page.features, key = { _, feature -> feature.key }) { index, feature ->
            PreferenceSegment(first = index == 0, last = index == page.features.lastIndex) {
                FeatureCard(
                    item = feature,
                    searchQuery = if (page.isSearchActive) page.searchQuery else "",
                    onOpenDetails = { viewModel.openFeatureDetails(feature.key) },
                    onFeatureEnabledChange = { viewModel.setFeatureEnabled(feature.key, it) },
                    onFeatureClick = { onFeatureClick(feature.key) },
                )
            }
        }
    } else if (page.categories.isEmpty() && !page.isSearchActive && !page.isErrorOverviewActive) {
        item(key = "empty") { EmptyStateCard(MiuixIcons.Folder, "暂无可用内容") }
    }
    if (isRoot) {
        settingsGroupTitle("通用")
        item(key = "appearance_entry") {
            PreferenceSegment(first = true, last = false) {
                ArrowPreference(title = "主题设置", insideMargin = SettingsRowPadding, onClick = viewModel::openAppearance)
            }
        }
        item(key = "backup_entry") {
            PreferenceSegment(first = false, last = false) {
                ArrowPreference(title = "备份与还原", insideMargin = SettingsRowPadding, onClick = onBackupRestoreClick)
            }
        }
        item(key = "feedback_entry") {
            PreferenceSegment(first = false, last = true) {
                ArrowPreference(
                    title = "反馈与建议",
                    insideMargin = SettingsRowPadding,
                    modifier = Modifier.combinedClickable(onClick = onIssueClick, onLongClick = onIssueLongClick),
                )
            }
        }
    }
}
