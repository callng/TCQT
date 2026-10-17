package com.owo233.tcqt.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val PageTransitionDurationMillis = 380

internal fun softFadeScaleIn(initialScale: Float, durationMillis: Int) =
    fadeIn(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        )

internal fun softFadeScaleOut(targetScale: Float, durationMillis: Int) =
    fadeOut(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        )

private fun String.navigationDepth(): Int = if (isEmpty()) 0 else count { it == '/' } + 1

private data class SettingsPageContentState(
    val path: String,
    val isAtRoot: Boolean,
    val isSearchActive: Boolean,
    val isErrorOverviewActive: Boolean,
    val searchQuery: String,
    val categories: List<CategoryUiState>,
    val features: List<FeatureItemUiState>,
    val enabledCount: Int,
    val disabledCount: Int,
    val errorCount: Int,
    val showSaveAction: Boolean,
    val currentCategoryLabel: String,
) {

    val animationKey: String
        get() =
            when {
                isErrorOverviewActive -> "errors"
                isSearchActive -> "search"
                else -> path
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
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackupRestoreClick: () -> Unit,
    isExportingBugReport: Boolean,
    onExportBugReportClick: () -> Unit,
    onFeatureClick: (String) -> Unit,
) {
    val hasPending by rememberUpdatedState(viewModel.hasPendingChanges)
    val isSearchActive = viewModel.isSearchActive
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val showSaveAction = hasPending && !isImeVisible
    val pageColor = MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        viewModel.reloadActionErrors()
    }

    Scaffold(
        containerColor = pageColor,
        topBar = {
            TopBar(
                viewModel = viewModel,
                isSearchActive = isSearchActive,
                searchQuery = viewModel.searchQuery,
                onSearchRequested = onSearchRequested,
                onSearchClosed = onSearchClosed,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onBackupRestoreClick = onBackupRestoreClick,
                isExportingBugReport = isExportingBugReport,
                onExportBugReportClick = onExportBugReportClick,
                themeMode = themeMode,
                monetEnabled = monetEnabled,
                onThemeModeChange = onThemeModeChange,
                onMonetEnabledChange = onMonetEnabledChange,
                containerColor = pageColor,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showSaveAction,
                enter =
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                        ),
                exit =
                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.85f,
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                        ),
            ) {
                SavePill(
                    pendingCount = viewModel.pendingChangeCount,
                    onClick = onSaveClick,
                )
            }
        },
    ) { innerPadding ->
        val categories by viewModel.currentCategories
        val features by viewModel.currentFeatures
        val errorFeatures by viewModel.errorFeatures
        val currentPath = viewModel.currentPath
        val isErrorOverviewActive = viewModel.isErrorOverviewActive
        val pageState =
            SettingsPageContentState(
                path = currentPath,
                isAtRoot = viewModel.isAtRoot,
                isSearchActive = isSearchActive,
                isErrorOverviewActive = isErrorOverviewActive,
                searchQuery = viewModel.searchQuery,
                categories = if (isErrorOverviewActive) emptyList() else categories,
                features = if (isErrorOverviewActive) errorFeatures else features,
                enabledCount = viewModel.enabledCount,
                disabledCount = viewModel.disabledCount,
                errorCount = viewModel.errorCount,
                showSaveAction = showSaveAction,
                currentCategoryLabel = viewModel.currentCategoryLabel.value,
            )

        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentKey = { it.animationKey },
            transitionSpec = {
                val forward =
                    targetState.isErrorOverviewActive ||
                        targetState.path.navigationDepth() >= initialState.path.navigationDepth()
                softFadeScaleIn(
                        initialScale = if (forward) 0.96f else 1.015f,
                        durationMillis = PageTransitionDurationMillis,
                    )
                    .togetherWith(
                        softFadeScaleOut(
                            targetScale = if (forward) 1.015f else 0.985f,
                            durationMillis = PageTransitionDurationMillis,
                        )
                    )
            },
            label = "settings_page_transition",
        ) { targetPageState ->
            PageContent(
                pageState = targetPageState,
                viewModel = viewModel,
                innerPadding = innerPadding,
                onIssueClick = onIssueClick,
                onIssueLongClick = onIssueLongClick,
                onFeatureClick = onFeatureClick,
            )
        }
    }
}

// ───── Page Content ─────

@Composable
private fun PageContent(
    pageState: SettingsPageContentState,
    viewModel: SettingViewModel,
    innerPadding: PaddingValues,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
    onFeatureClick: (String) -> Unit,
) {
    val categories = pageState.categories
    val features = pageState.features
    val isSearchActive = pageState.isSearchActive
    val isErrorOverviewActive = pageState.isErrorOverviewActive
    val lazyListState =
        remember(pageState.animationKey) {
            viewModel.getScrollState(pageState.animationKey)
        }

    AdaptiveContent { sidePadding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize().horizontalSafePadding().overScrollVertical().imePadding(),
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    start = sidePadding,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = sidePadding,
                    bottom =
                        innerPadding.calculateBottomPadding() +
                            if (pageState.showSaveAction) 100.dp else 24.dp,
                ),
            verticalArrangement = Arrangement.Top,
        ) {
            // Header (root only)
            if (!isSearchActive && !isErrorOverviewActive && pageState.isAtRoot) {
                item(key = "header") {
                    CompactHeaderCard(
                        hostName = HookEnv.appName,
                        hostVersion =
                            "${HookEnv.versionName} (${HookEnv.versionCode}) ${PlatformTools.getHostChannel()}",
                        moduleName = TCQTBuild.APP_NAME,
                        moduleVersion =
                            "${TCQTBuild.VER_NAME} ${if (TCQTBuild.DEBUG) "D" else "R"}",
                        enabledCount = pageState.enabledCount,
                        disabledCount = pageState.disabledCount,
                        errorCount = pageState.errorCount,
                        onErrorClick = viewModel::openErrorOverview,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (isErrorOverviewActive) {
                item(key = "error_overview_intro") {
                    ErrorOverviewHeader(errorCount = pageState.errorCount)
                }
            }

            // Search: prompt before typing or history
            if (isSearchActive && pageState.searchQuery.isBlank()) {
                if (viewModel.searchHistory.isEmpty()) {
                    item(key = "search_prompt") {
                        SearchPromptCard()
                    }
                } else {
                    item(key = "search_history") {
                        SearchHistoryLayout(
                            history = viewModel.searchHistory,
                            onTagClick = { tag ->
                                viewModel.updateSearchQuery(tag)
                            },
                            onClearClick = {
                                viewModel.clearSearchHistory()
                            },
                        )
                    }
                }
            }

            // Search: result count (only when there are results)
            if (isSearchActive && pageState.searchQuery.isNotBlank() && features.isNotEmpty()) {
                item(key = "search_result_count") {
                    Text(
                        text = "共找到 ${features.size} 项",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // Search: zero results
            if (isSearchActive && pageState.searchQuery.isNotBlank() && features.isEmpty()) {
                item(key = "search_empty") {
                    EmptyStateCard(
                        icon = MiuixIcons.Search,
                        title = "未找到相关功能",
                        summary = "换一个关键词试试",
                    )
                }
            }

            // Sub-category title (non-root, non-search)
            if (
                !isSearchActive &&
                    !isErrorOverviewActive &&
                    !pageState.isAtRoot &&
                    categories.isNotEmpty()
            ) {
                item(key = "subcat_title") {
                    SmallTitle(text = "分类")
                }
            }

            // Category cards
            if (!isSearchActive && !isErrorOverviewActive) {
                itemsIndexed(
                    items = categories,
                    key = { _, category -> "cat_${category.fullPath}" },
                    contentType = { _, _ -> "category_row" },
                ) { index, category ->
                    PreferenceSegment(first = index == 0, last = index == categories.lastIndex) {
                        CategoryCard(
                            category = category,
                            onClick = { viewModel.navigateTo(category.fullPath) },
                        )
                    }
                }

                // Separator between categories and features
                if (categories.isNotEmpty() && features.isNotEmpty()) {
                    item(key = "separator") {
                        SmallTitle(text = "功能")
                    }
                }
            }

            // Feature cards
            if (
                features.isEmpty() &&
                    categories.isEmpty() &&
                    !isSearchActive &&
                    !isErrorOverviewActive
            ) {
                item(key = "empty") {
                    EmptyStateCard(
                        icon = MiuixIcons.Folder,
                        title = "暂无可用内容",
                    )
                }
            } else if (features.isNotEmpty()) {
                itemsIndexed(
                    items = features,
                    key = { _, feature -> feature.key },
                    contentType = { _, _ -> "feature_row" },
                ) { index, item ->
                    PreferenceSegment(first = index == 0, last = index == features.lastIndex) {
                        FeatureCard(
                            item = item,
                            searchQuery = pageState.searchQuery,
                            onToggleExpanded = { viewModel.toggleExpanded(item.key) },
                            onFeatureEnabledChange = { viewModel.setFeatureEnabled(item.key, it) },
                            onOptionValueChange = { key, value ->
                                viewModel.setOptionValue(key, value)
                            },
                            onSliderValueChange = { key, value ->
                                viewModel.setSliderValue(key, value)
                            },
                            onTextValueChange = { key, value ->
                                viewModel.setTextValue(key, value)
                            },
                            onClearError = { viewModel.clearActionError(item.key) },
                            onFeatureClick = { onFeatureClick(item.key) },
                            forceExpanded = isSearchActive,
                        )
                    }
                }
            }

            if (!isSearchActive && !isErrorOverviewActive) {
                item(key = "footer") {
                    FooterCard(onIssueClick = onIssueClick, onIssueLongClick = onIssueLongClick)
                }
            }
        }
    }
}

// ───── Top Bar ─────
