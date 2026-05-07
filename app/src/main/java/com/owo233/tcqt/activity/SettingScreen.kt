@file:OptIn(ExperimentalFoundationApi::class)

package com.owo233.tcqt.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.utils.PlatformTools

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
    val searchQuery: String,
    val categories: List<CategoryUiState>,
    val features: List<FeatureItemUiState>,
    val enabledCount: Int,
    val disabledCount: Int,
    val hasPending: Boolean
) {
    val animationKey: String
        get() = if (isSearchActive) "search" else path
}

private enum class SettingsTopBarMode { Search, SubPage, Root }

private data class SettingsTopBarState(
    val mode: SettingsTopBarMode,
    val path: String,
    val breadcrumbs: List<BreadcrumbItem>,
    val searchQuery: String
) {
    val animationKey: String
        get() = when (mode) {
            SettingsTopBarMode.Search -> "search"
            SettingsTopBarMode.SubPage -> "sub_$path"
            SettingsTopBarMode.Root -> "root"
        }
}

// ───── Main Screen ─────

@Composable
fun SettingScreen(
    viewModel: SettingViewModel,
    snackbarHostState: SnackbarHostState,
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackupRestoreClick: () -> Unit
) {
    val hasPending by rememberUpdatedState(viewModel.hasPendingChanges)
    val isSearchActive = viewModel.isSearchActive

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                viewModel = viewModel,
                isSearchActive = isSearchActive,
                searchQuery = viewModel.searchQuery,
                onSearchRequested = onSearchRequested,
                onSearchClosed = onSearchClosed,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onClearQuery = viewModel::clearSearchQuery,
                onBackupRestoreClick = onBackupRestoreClick
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = hasPending,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing))
                        + scaleIn(initialScale = 0.85f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                        + scaleOut(targetScale = 0.85f, animationSpec = tween(180, easing = FastOutSlowInEasing))
            ) {
                SavePill(
                    hasPendingChanges = hasPending,
                    pendingCount = viewModel.pendingChangeCount,
                    onClick = onSaveClick
                )
            }
        }
    ) { innerPadding ->
        val categories by viewModel.currentCategories
        val features by viewModel.currentFeatures
        val currentPath = viewModel.currentPath
        val pageState = SettingsPageContentState(
            path = currentPath,
            isAtRoot = viewModel.isAtRoot,
            isSearchActive = isSearchActive,
            searchQuery = viewModel.searchQuery,
            categories = categories,
            features = features,
            enabledCount = viewModel.enabledCount,
            disabledCount = viewModel.disabledCount,
            hasPending = hasPending
        )

        AnimatedContent(
            targetState = pageState,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.animationKey },
            transitionSpec = {
                val forward = targetState.path.navigationDepth() >= initialState.path.navigationDepth()
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
                onIssueLongClick = onIssueLongClick
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
    onIssueLongClick: () -> Unit
) {
    val categories = pageState.categories
    val features = pageState.features
    val isSearchActive = pageState.isSearchActive
    val lazyListState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        state = lazyListState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = if (pageState.hasPending) 112.dp else 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header (root only)
        if (!isSearchActive && pageState.isAtRoot) {
            item(key = "header") {
                CompactHeaderCard(
                    hostName = PlatformTools.getHostName(),
                    hostVersion = "${PlatformTools.getHostVersion()} (${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}",
                    moduleName = TCQTBuild.APP_NAME,
                    moduleVersion = TCQTBuild.VER_NAME,
                    enabledCount = pageState.enabledCount,
                    disabledCount = pageState.disabledCount
                )

            }
        }

        // Search: prompt before typing
        if (isSearchActive && pageState.searchQuery.isBlank()) {
            item(key = "search_prompt") {

                SearchPromptCard()
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
                EmptyStateCard()
            }
        }

        // Sub-category title (non-root, non-search)
        if (!isSearchActive && !pageState.isAtRoot && categories.isNotEmpty()) {
            item(key = "subcat_title") {
                Text(
                    text = "子分类",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        // Category cards
        if (!isSearchActive) {
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
        if (features.isEmpty() && categories.isEmpty() && !isSearchActive) {
            item(key = "empty") {
                EmptyStateCard()
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
                    forceExpanded = isSearchActive
                )
            }
        }

        if (!isSearchActive) {
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
    onClearQuery: () -> Unit,
    onBackupRestoreClick: () -> Unit
) {
    val breadcrumbs by viewModel.breadcrumbs
    val topBarState = SettingsTopBarState(
        mode = when {
            isSearchActive -> SettingsTopBarMode.Search
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
            .background(MaterialTheme.colorScheme.background)
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
                    onClearClick = onClearQuery
                )

                SettingsTopBarMode.SubPage -> {
                    // ─── Sub-page: back + breadcrumbs + search ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Inline breadcrumbs
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.breadcrumbs.forEachIndexed { index, crumb ->
                                if (index > 0) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { viewModel.navigateToBreadcrumb(crumb.fullPath) }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                if (index < state.breadcrumbs.lastIndex) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }

                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsTopBarMode.Root -> {
                    // ─── Root page: right-aligned search + backup ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(onClick = onBackupRestoreClick) {
                            Icon(
                                imageVector = Icons.Rounded.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "备份",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

}

// ───── Category Card ─────

@Composable
private fun CategoryCard(category: CategoryUiState, onClick: () -> Unit) {
    val accent = if (category.enabledCount > 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val borderColor = if (category.enabledCount > 0)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (category.enabledCount > 0) 1.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (category.enabledCount > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (category.isLeaf) Icons.Rounded.ToggleOn
                        else Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = "已启用 ${category.enabledCount}",
                        containerColor = if (category.enabledCount > 0)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (category.enabledCount > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(
                        text = "共 ${category.totalFeatureCount} 项",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
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
    disabledCount: Int
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${TCQTBuild.APP_NAME} 模块配置",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                CombinedInfoCard(
                    hostName = hostName,
                    hostVersion = hostVersion,
                    moduleName = moduleName,
                    moduleVersion = moduleVersion,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallStatCard(
                    title = "已启用",
                    value = enabledCount.toString(),
                    accent = Color(0xFF1E8E3E),
                    modifier = Modifier.weight(1f)
                )
                SmallStatCard(
                    title = "未启用",
                    value = disabledCount.toString(),
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CombinedInfoCard(
    hostName: String,
    hostVersion: String,
    moduleName: String,
    moduleVersion: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.wrapContentWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoRow(title = "宿主环境", value = "$hostName\n$hostVersion")
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 2.dp)
            )
            InfoRow(title = "模块版本", value = "$moduleName\n$moduleVersion")
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SmallStatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───── Search Prompt (animated) ─────

@Composable
private fun SearchPromptCard() {
    val infinite = rememberInfiniteTransition(label = "search_prompt")
    val iconScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )
    val ringScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Expanding ring
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                }
        ) {}

        // Core icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
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
    onClearClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            placeholder = { Text("搜索功能名称或描述") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (query.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClearClick
                            )
                    )
                }
            }
        )
    }
}

@Composable
private fun EmptyStateCard() {
    val infinite = rememberInfiniteTransition(label = "empty_state")
    val bounce by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer { translationY = bounce }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
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
private fun SavePill(hasPendingChanges: Boolean, pendingCount: Int, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    val pulseScale = if (hasPendingChanges) {
        val infiniteTransition = rememberInfiniteTransition(label = "savePulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "savePulseValue"
        )
    } else null

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (hasPendingChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (hasPendingChanges) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (hasPendingChanges) 8.dp else 0.dp,
        modifier = Modifier
            .graphicsLayer {
                val scale = pulseScale?.value ?: 1f
                scaleX = scale; scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (hasPendingChanges) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Check, contentDescription = "保存")
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
    forceExpanded: Boolean = false
) {
    val effectivelyExpanded = item.expanded || forceExpanded
    val feature = item.feature
    val query = searchQuery.trim()
    val borderColor = when {
        effectivelyExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        item.hasPending -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        item.enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (item.expanded || item.enabled) 1.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        enabled = feature.expandable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = rememberHighlightedText(feature.label, query),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (feature.expandable) {
                            Icon(
                                imageVector = if (effectivelyExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            text = if (item.enabled) "已启用" else "未启用",
                            containerColor = if (item.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (item.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.hasPending) StatusPill(
                            text = "未保存",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (item.optionGroup != null) StatusPill(
                            text = "选项",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.textAreas.isNotEmpty()) StatusPill(
                            text = "文本 ${item.textAreas.size}",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = item.enabled, onCheckedChange = onFeatureEnabledChange, modifier = Modifier.padding(start = 12.dp))
            }

            AnimatedVisibility(visible = effectivelyExpanded && feature.expandable) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (feature.desc.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Text(
                            text = rememberHighlightedText(feature.desc, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item.optionGroup?.let { group ->
                        if (feature.desc.isBlank())
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        OptionGroup(group = group, currentValue = item.optionValue ?: group.fallbackValue, onValueChange = onOptionValueChange)
                    }
                    item.textAreas.forEach { area ->
                        OutlinedTextField(
                            value = area.value,
                            onValueChange = { value -> onTextValueChange(area.key, value) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(area.label) },
                            placeholder = { Text(area.placeholder.ifBlank { area.label }) },
                            minLines = 3,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
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
                if (index < 0) { append(text.substring(cursor)); break }
                if (index > cursor) append(text.substring(cursor, index))
                pushStyle(SpanStyle(color = highlightColor, background = highlightBackground, fontWeight = FontWeight.Bold))
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.options.forEachIndexed { index, option ->
            val mask = group.resolveMask(option, index)
            val selected = if (group.isMulti) (currentValue and mask) != 0 else currentValue == option.value
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(if (group.isMulti) currentValue xor mask else option.value)
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(18.dp).background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = if (group.isMulti) RoundedCornerShape(5.dp) else CircleShape
                        )
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
