@file:OptIn(ExperimentalFoundationApi::class)

package com.owo233.tcqt.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionUiType
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.internals.setting.ModuleThemeMode
import com.owo233.tcqt.ui.miuix.MaterialTheme
import com.owo233.tcqt.ui.miuix.TextButton
import com.owo233.tcqt.utils.PlatformTools
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PageTransitionDurationMillis = 380
private const val TopBarTransitionDurationMillis = 340

private fun softFadeScaleIn(initialScale: Float, durationMillis: Int) =
    fadeIn(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
            scaleIn(
                initialScale = initialScale,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
            )

private fun softFadeScaleOut(targetScale: Float, durationMillis: Int) =
    fadeOut(animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) +
            scaleOut(
                targetScale = targetScale,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
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
    val currentCategoryLabel: String
) {

    val animationKey: String
        get() = when {
            isErrorOverviewActive -> "errors"
            isSearchActive -> "search"
            else -> path
        }
}

private enum class SettingsTopBarMode { Search, ErrorOverview, SubPage, Root }

private data class SettingsTopBarState(
    val mode: SettingsTopBarMode,
    val path: String,
    val breadcrumbs: List<BreadcrumbItem>,
    val searchQuery: String
) {

    val animationKey: String
        get() = when (mode) {
            SettingsTopBarMode.Search -> "search"
            SettingsTopBarMode.ErrorOverview -> "errors"
            SettingsTopBarMode.SubPage -> "sub_$path"
            SettingsTopBarMode.Root -> "root"
        }
}

// ───── Main Screen ─────

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
    onFeatureClick: (String) -> Unit
) {
    val hasPending by rememberUpdatedState(viewModel.hasPendingChanges)
    val isSearchActive = viewModel.isSearchActive
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val showSaveAction = hasPending && !isImeVisible
    val isDark = themeMode.resolveDark(isSystemInDarkTheme())
    val pageColor = if (monetEnabled) {
        MaterialTheme.colorScheme.background
    } else if (isDark) {
        Color(0xFF101010)
    } else {
        Color(0xFFF5F5F5)
    }

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
            )
        },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showSaveAction,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing))
                        + scaleIn(initialScale = 0.85f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                        + scaleOut(targetScale = 0.85f, animationSpec = tween(180, easing = FastOutSlowInEasing))
            ) {
                SavePill(
                    pendingCount = viewModel.pendingChangeCount,
                    onClick = onSaveClick
                )
            }
        }
    ) { innerPadding ->
        val categories by viewModel.currentCategories
        val features by viewModel.currentFeatures
        val errorFeatures by viewModel.errorFeatures
        val currentPath = viewModel.currentPath
        val isErrorOverviewActive = viewModel.isErrorOverviewActive
        val pageState = SettingsPageContentState(
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
            currentCategoryLabel = viewModel.currentCategoryLabel.value
        )

        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.animationKey },
            transitionSpec = {
                val forward = targetState.isErrorOverviewActive ||
                        targetState.path.navigationDepth() >= initialState.path.navigationDepth()
                softFadeScaleIn(
                    initialScale = if (forward) 0.96f else 1.015f,
                    durationMillis = PageTransitionDurationMillis
                ).togetherWith(
                    softFadeScaleOut(
                        targetScale = if (forward) 1.015f else 0.985f,
                        durationMillis = PageTransitionDurationMillis
                    )
                )

            },
            label = "settings_page_transition"
        ) { targetPageState ->
            PageContent(
                pageState = targetPageState,
                viewModel = viewModel,
                innerPadding = innerPadding,
                onIssueClick = onIssueClick,
                onIssueLongClick = onIssueLongClick,
                onFeatureClick = onFeatureClick
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
    onFeatureClick: (String) -> Unit
) {
    val categories = pageState.categories
    val features = pageState.features
    val isSearchActive = pageState.isSearchActive
    val isErrorOverviewActive = pageState.isErrorOverviewActive
    val lazyListState = remember(pageState.animationKey) {
        viewModel.getScrollState(pageState.animationKey)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
        state = lazyListState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = if (pageState.showSaveAction) 112.dp else 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header (root only)
        if (!isSearchActive && !isErrorOverviewActive && pageState.isAtRoot) {
            item(key = "header") {
                CompactHeaderCard(
                    hostName = HookEnv.appName,
                    hostVersion = "${HookEnv.versionName} (${HookEnv.versionCode}) ${PlatformTools.getHostChannel()}",
                    moduleName = TCQTBuild.APP_NAME,
                    moduleVersion = "${TCQTBuild.VER_NAME} ${if (TCQTBuild.DEBUG) "D" else "R"}",
                    enabledCount = pageState.enabledCount,
                    disabledCount = pageState.disabledCount,
                    errorCount = pageState.errorCount,
                    onErrorClick = viewModel::openErrorOverview
                )
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
                        }
                    )
                }
            }
        }

        // Search: result count (only when there are results)
        if (isSearchActive && pageState.searchQuery.isNotBlank() && features.isNotEmpty()) {
            item(key = "search_result_count") {
                Text(
                    text = "共找到 ${features.size} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
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
        if (!isSearchActive && !isErrorOverviewActive && !pageState.isAtRoot && categories.isNotEmpty()) {
            item(key = "subcat_title") {
                Text(
                    text = pageState.currentCategoryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        // Category cards
        if (!isSearchActive && !isErrorOverviewActive) {
            items(
                items = categories,
                key = { "cat_${it.fullPath}" },
                contentType = { "category_card" }
            ) { category ->
                CategoryCard(
                    category = category,
                    onClick = { viewModel.navigateTo(category.fullPath) }
                )
            }

            // Separator between categories and features
            if (categories.isNotEmpty() && features.isNotEmpty()) {
                item(key = "separator") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Feature cards
        if (features.isEmpty() && categories.isEmpty() && !isSearchActive && !isErrorOverviewActive) {
            item(key = "empty") {
                EmptyStateCard(
                    icon = MiuixIcons.Folder,
                    title = "暂无可用内容",
                )
            }
        } else if (features.isNotEmpty()) {
            items(
                items = features,
                key = { it.key },
                contentType = { "feature_card" }
            ) { item ->
                FeatureCard(
                    item = item,
                    searchQuery = pageState.searchQuery,
                    onToggleExpanded = { viewModel.toggleExpanded(item.key) },
                    onFeatureEnabledChange = { viewModel.setFeatureEnabled(item.key, it) },
                    onOptionValueChange = { item.optionGroup?.let { g -> viewModel.setOptionValue(g.key, it) } },
                    onTextValueChange = { key, value -> viewModel.setTextValue(key, value) },
                    onClearError = { viewModel.clearActionError(item.key) },
                    onFeatureClick = { onFeatureClick(item.key) },
                    forceExpanded = isSearchActive,
                )
            }
        }

        if (!isSearchActive && !isErrorOverviewActive) {
            item(key = "footer") {
                FooterCard(onIssueClick = onIssueClick, onIssueLongClick = onIssueLongClick)
            }
        }
    }
}

// ───── Top Bar ─────

@Composable
private fun TopBar(
    viewModel: SettingViewModel,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackupRestoreClick: () -> Unit,
    isExportingBugReport: Boolean,
    onExportBugReportClick: () -> Unit,
    themeMode: ModuleThemeMode,
    monetEnabled: Boolean,
    onThemeModeChange: (ModuleThemeMode) -> Unit,
    onMonetEnabledChange: (Boolean) -> Unit,
    containerColor: Color,
) {
    val breadcrumbs by viewModel.breadcrumbs
    val topBarState = SettingsTopBarState(
        mode = when {
            isSearchActive -> SettingsTopBarMode.Search
            viewModel.isErrorOverviewActive -> SettingsTopBarMode.ErrorOverview
            !viewModel.isAtRoot -> SettingsTopBarMode.SubPage
            else -> SettingsTopBarMode.Root
        },
        path = viewModel.currentPath,
        breadcrumbs = breadcrumbs,
        searchQuery = searchQuery
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = topBarState,
            modifier = Modifier.fillMaxWidth(),
            contentKey = { it.animationKey },
            transitionSpec = {
                softFadeScaleIn(
                    initialScale = 0.98f,
                    durationMillis = TopBarTransitionDurationMillis
                ).togetherWith(
                    softFadeScaleOut(
                        targetScale = 0.98f,
                        durationMillis = TopBarTransitionDurationMillis
                    )
                )

            },
            label = "settings_top_bar_transition"
        ) { state ->
            when (state.mode) {
                SettingsTopBarMode.Search -> SearchBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onBackClick = onSearchClosed,
                )

                SettingsTopBarMode.ErrorOverview -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = MiuixIcons.Report,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "功能异常",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusPill(
                            text = "${viewModel.errorCount} 项",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onExportBugReportClick,
                            enabled = !isExportingBugReport,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Share,
                                contentDescription = "导出并分享异常报告",
                                tint = if (isExportingBugReport) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }

                SettingsTopBarMode.SubPage -> {
                    // ─── Sub-page: back + breadcrumbs + search ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Scrollable breadcrumbs to prevent wrapping/clipping
                        val scrollState = rememberScrollState()
                        LaunchedEffect(state.breadcrumbs.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.breadcrumbs.forEachIndexed { index, crumb ->
                                if (index > 0) {
                                    Icon(
                                        imageVector = MiuixIcons.ChevronForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                Text(
                                    text = crumb.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (index == state.breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == state.breadcrumbs.lastIndex) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.navigateToBreadcrumb(crumb.fullPath) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                if (index < state.breadcrumbs.lastIndex) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }

                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsTopBarMode.Root -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onBackupRestoreClick) {
                            Icon(
                                imageVector = MiuixIcons.Backup,
                                contentDescription = "备份与清理",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        ThemeMenuButton(
                            themeMode = themeMode,
                            monetEnabled = monetEnabled,
                            onThemeModeChange = onThemeModeChange,
                            onMonetEnabledChange = onMonetEnabledChange,
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun ThemeMenuButton(
    themeMode: ModuleThemeMode,
    monetEnabled: Boolean,
    onThemeModeChange: (ModuleThemeMode) -> Unit,
    onMonetEnabledChange: (Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = MiuixIcons.Theme,
                contentDescription = "主题",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OverlayListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = false,
            minWidth = 220.dp,
            onDismissRequest = { showMenu = false },
        ) {
            ListPopupColumn {
                ModuleThemeMode.entries.forEachIndexed { index, mode ->
                    val label = when (mode) {
                        ModuleThemeMode.System -> "跟随系统"
                        ModuleThemeMode.Light -> "浅色模式"
                        ModuleThemeMode.Dark -> "深色模式"
                    }
                    DropdownImpl(
                        item = DropdownItem(text = label),
                        optionSize = ModuleThemeMode.entries.size + 1,
                        isSelected = themeMode == mode,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            onThemeModeChange(mode)
                        },
                    )
                }
                DropdownImpl(
                    item = DropdownItem(
                        text = "Monet 动态色",
                    ),
                    optionSize = ModuleThemeMode.entries.size + 1,
                    isSelected = monetEnabled,
                    index = ModuleThemeMode.entries.size,
                    onSelectedIndexChange = {
                        showMenu = false
                        onMonetEnabledChange(!monetEnabled)
                    },
                )
            }
        }
    }
}

// ───── Category Card ─────

@Composable
private fun CategoryCard(category: CategoryUiState, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        ArrowPreference(
            title = category.label,
            summary = "已启用 ${category.enabledCount} · 共 ${category.totalFeatureCount} 项",
            startAction = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (category.isLeaf) MiuixIcons.Tune else MiuixIcons.Folder,
                            contentDescription = null,
                            tint = if (category.enabledCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick,
        )
    }
}

// ───── Compact Header ─────

@Composable
private fun CompactHeaderCard(
    hostName: String,
    hostVersion: String,
    moduleName: String,
    moduleVersion: String,
    enabledCount: Int,
    disabledCount: Int,
    errorCount: Int,
    onErrorClick: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "已连接 $hostName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hostVersion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$moduleName $moduleVersion",
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val currentTime = System.currentTimeMillis()
                                clickCount = if (currentTime - lastClickTime < 500) clickCount + 1 else 1
                                lastClickTime = currentTime
                                if (clickCount >= 5) {
                                    clickCount = 0
                                    Toasts.info("编译时间: ${TCQTBuild.BUILD_TIME}")
                                }
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverviewStat(
                    title = "已启用",
                    value = enabledCount,
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewDivider()
                OverviewStat(
                    title = "未启用",
                    value = disabledCount,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OverviewDivider()
                OverviewStat(
                    title = "异常",
                    value = errorCount,
                    valueColor = if (errorCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = if (errorCount > 0) onErrorClick else null,
                    modifier = Modifier.weight(1f),
                )
            }

        }
    }
}

@Composable
private fun OverviewStat(
    title: String,
    value: Int,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
    )
}

@Composable
private fun ErrorOverviewHeader(errorCount: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = MiuixIcons.Report,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "$errorCount 个功能存在异常",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "点击下方功能可展开查看异常阶段与完整日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}

// ───── Search Prompt ─────

@Composable
private fun SearchPromptCard() {
    EmptyStateCard(
        icon = MiuixIcons.Search,
        title = "搜索模块功能",
        summary = "输入功能名称或描述",
    )
}

// ───── Search History ─────

@Composable
private fun SearchHistoryLayout(
    history: List<String>,
    onTagClick: (String) -> Unit,
    onClearClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近搜索",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = MiuixIcons.Clear,
                        contentDescription = "清除搜索记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTagClick(tag) },
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ───── Search Bar ─────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {},
            expanded = true,
            onExpandedChange = { expanded ->
                if (!expanded) onBackClick()
            },
            modifier = Modifier
                .weight(1f),
            label = "搜索功能名称或描述",
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    summary: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(58.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FooterCard(onIssueClick: () -> Unit, onIssueLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.combinedClickable(
                onClick = onIssueClick,
                onLongClick = onIssueLongClick
            )
        ) {
            Text(
                text = "反馈问题与建议",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = "${TCQTBuild.APP_NAME} Module © ${TCQTBuild.COPYRIGHT_YEAR}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ───── Save Pill ─────

@Composable
private fun SavePill(pendingCount: Int, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = MiuixIcons.Ok, contentDescription = "保存")
            Text(
                text = if (pendingCount > 0) "保存配置 · $pendingCount" else "保存配置",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ───── Feature Card ─────

@Composable
private fun FeatureCard(
    item: FeatureItemUiState,
    searchQuery: String,
    onToggleExpanded: () -> Unit,
    onFeatureEnabledChange: (Boolean) -> Unit,
    onOptionValueChange: (Int) -> Unit,
    onTextValueChange: (String, String) -> Unit,
    onClearError: () -> Unit,
    onFeatureClick: () -> Unit,
    forceExpanded: Boolean = false,
) {
    var searchExpanded by remember(item.key, searchQuery, forceExpanded) {
        mutableStateOf(forceExpanded)
    }
    val effectivelyExpanded =
        (if (forceExpanded) searchExpanded else item.expanded) || item.error != null
    val toggleDetails = {
        if (forceExpanded) {
            searchExpanded = !searchExpanded
        } else {
            onToggleExpanded()
        }
    }
    val feature = item.feature
    val hasDetails = item.error != null || item.optionGroup != null || item.textAreas.isNotEmpty()
    val query = searchQuery.trim()
    val featureTitleColor =
        if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val titleColor = BasicComponentDefaults.titleColor(
        color = featureTitleColor,
    )
    val bottomAction: (@Composable () -> Unit)? =
        if (item.hasPending || item.error != null || (effectivelyExpanded && hasDetails)) {
            {
                FeaturePreferenceDetails(
                    item = item,
                    expanded = effectivelyExpanded,
                    onOptionValueChange = onOptionValueChange,
                    onTextValueChange = onTextValueChange,
                    onClearError = onClearError,
                )
            }
        } else {
            null
        }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (item.error != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (query.isNotBlank()) {
            BasicComponent(
                endActions = {
                    when (item.uiType) {
                        ActionUiType.ENTRY -> {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        else -> {
                            if (hasDetails && item.error == null) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(20.dp)
                                        .graphicsLayer {
                                            rotationZ = if (effectivelyExpanded) -90f else 90f
                                        },
                                )
                            }
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = onFeatureEnabledChange,
                            )
                        }
                    }
                },
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                onClick = when {
                    item.uiType == ActionUiType.ENTRY -> onFeatureClick
                    hasDetails && item.error == null -> toggleDetails
                    hasDetails -> null
                    else -> { { onFeatureEnabledChange(!item.enabled) } }
                },
            ) {
                Text(
                    text = rememberHighlightedText(feature.label, query),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = featureTitleColor,
                )
                if (feature.desc.isNotBlank()) {
                    Text(
                        text = rememberHighlightedText(feature.desc, query),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (item.uiType == ActionUiType.ENTRY) {
            ArrowPreference(
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                onClick = onFeatureClick,
            )
        } else if (hasDetails) {
            BasicComponent(
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                endActions = {
                    if (item.error == null) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp)
                                .graphicsLayer {
                                    rotationZ = if (effectivelyExpanded) -90f else 90f
                                },
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = onFeatureEnabledChange,
                    )
                },
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                onClick = if (item.error == null) toggleDetails else null,
                onClickLabel = if (effectivelyExpanded) "收起详细设置" else "展开详细设置",
            )
        } else {
            SwitchPreference(
                checked = item.enabled,
                onCheckedChange = onFeatureEnabledChange,
                title = feature.label,
                summary = feature.desc.takeIf(String::isNotBlank),
                titleColor = titleColor,
                bottomAction = bottomAction,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun FeaturePreferenceDetails(
    item: FeatureItemUiState,
    expanded: Boolean,
    onOptionValueChange: (Int) -> Unit,
    onTextValueChange: (String, String) -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.error != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = "运行异常",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && (
                item.error != null ||
                    item.optionGroup != null ||
                    item.textAreas.isNotEmpty()
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(
                    color = if (item.error != null) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    },
                )
                item.error?.let { error ->
                    FeatureErrorPanel(error = error, onClear = onClearError)
                }
                item.optionGroup?.let { group ->
                    OptionGroup(
                        group = group,
                        currentValue = item.optionValue ?: group.fallbackValue,
                        onValueChange = onOptionValueChange,
                    )
                }
                item.textAreas.forEach { area ->
                    FeatureTextArea(
                        area = area,
                        repositionKey = item.hasPending,
                        onValueChange = { value -> onTextValueChange(area.key, value) },
                    )
                }
            }
        }

        if (item.hasPending) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = "未保存",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FeatureTextArea(
    area: TextAreaUiState,
    repositionKey: Any?,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused, repositionKey) {
        if (isFocused) {
            delay(32)
            bringIntoViewRequester.bringIntoView()
            delay(288)
            bringIntoViewRequester.bringIntoView()
        }
    }

    OutlinedTextField(
        value = area.value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        label = area.label,
        useLabelAsPlaceholder = area.value.isBlank(),
        minLines = 3,
        cornerRadius = 14.dp,
    )
}

@Composable
private fun FeatureErrorPanel(error: FeatureErrorUiState, onClear: () -> Unit) {
    val occurredAt = remember(error.occurredAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.occurredAt))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Report,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "异常日志",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "$occurredAt · ${error.processName} · ${error.stage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f)
                    )
                }
                TextButton(onClick = onClear) {
                    Text("清除")
                }
            }
            Text(
                text = error.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            SelectionContainer {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error.details,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberHighlightedText(text: String, keyword: String): AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    return remember(text, keyword, colorScheme) {
        if (keyword.isBlank() || text.isBlank()) return@remember AnnotatedString(text)
        val lowerText = text.lowercase()
        val lowerKeyword = keyword.lowercase()
        val highlightBackground = colorScheme.primaryContainer.copy(alpha = 0.9f)
        val highlightColor = colorScheme.primary

        buildAnnotatedString {
            var cursor = 0
            while (cursor < text.length) {
                val index = lowerText.indexOf(lowerKeyword, startIndex = cursor)
                if (index < 0) {
                    append(text.substring(cursor))
                    break
                }
                if (index > cursor) append(text.substring(cursor, index))
                pushStyle(
                    SpanStyle(
                        color = highlightColor,
                        background = highlightBackground,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                append(text.substring(index, index + keyword.length))
                pop()
                cursor = index + keyword.length
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OptionGroup(group: FeatureOptionGroup, currentValue: Int, onValueChange: (Int) -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            group.options.forEachIndexed { index, option ->
                val mask = group.resolveMask(option, index)
                val selected = if (group.isMulti) (currentValue and mask) != 0 else currentValue == option.value
                val changeSelection = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(if (group.isMulti) currentValue xor mask else option.value)
                }

                if (group.isMulti) {
                    CheckboxPreference(
                        title = option.label,
                        checked = selected,
                        onCheckedChange = { changeSelection() },
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    )
                } else {
                    RadioButtonPreference(
                        title = option.label,
                        selected = selected,
                        onClick = changeSelection,
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                if (index < group.options.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                    )
                }
            }
        }
    }
}
