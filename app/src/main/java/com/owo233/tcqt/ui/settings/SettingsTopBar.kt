package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.horizontalSafePadding
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TopBar(
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
    scrollBehavior: ScrollBehavior,
) {
    if (isSearchActive) {
        Box(
            Modifier.statusBarsPadding()
                .horizontalSafePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onBackClick = onSearchClosed,
            )
        }
        return
    }
    val breadcrumbs by viewModel.breadcrumbs
    TopAppBar(
        title =
            when {
                viewModel.isErrorOverviewActive -> "功能异常"
                viewModel.isAtRoot -> "TCQT"
                else -> viewModel.currentCategoryLabel.value
            },
        color = containerColor,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (!viewModel.isAtRoot || viewModel.isErrorOverviewActive) {
                IconButton(onClick = { viewModel.navigateUp() }) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            }
        },
        actions = {
            if (viewModel.isErrorOverviewActive) {
                IconButton(onClick = onExportBugReportClick, enabled = !isExportingBugReport) {
                    Icon(MiuixIcons.Share, contentDescription = "导出并分享异常报告")
                }
            } else {
                IconButton(onClick = onSearchRequested) {
                    Icon(MiuixIcons.Search, contentDescription = "搜索")
                }
                if (viewModel.isAtRoot) {
                    IconButton(onClick = onBackupRestoreClick) {
                        Icon(MiuixIcons.Backup, contentDescription = "备份与清理")
                    }
                    ThemeMenuButton(
                        themeMode,
                        monetEnabled,
                        onThemeModeChange,
                        onMonetEnabledChange,
                    )
                }
            }
        },
        bottomContent = {
            if (!viewModel.isAtRoot && !viewModel.isErrorOverviewActive) {
                // Breadcrumbs retain direct navigation to every parent, with full touch targets.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    breadcrumbs.dropLast(1).forEach { crumb ->
                        ActionButton(onClick = { viewModel.navigateToBreadcrumb(crumb.fullPath) }) {
                            Text(crumb.name)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        },
    )
}

@Composable
internal fun ThemeMenuButton(
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
                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                    val label =
                        when (mode) {
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
                    item = DropdownItem(text = "Monet 动态色"),
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
