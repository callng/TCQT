package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.horizontalSafePadding
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share

@Composable
internal fun TopBar(
    title: String,
    showBack: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onBack: () -> Unit,
    onSearchRequested: (() -> Unit)?,
    onSearchClosed: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    isErrorOverviewActive: Boolean,
    isExportingBugReport: Boolean,
    onExportBugReportClick: () -> Unit,
    scrollBehavior: ScrollBehavior?,
) {
    if (isSearchActive) {
        Box(
            Modifier.statusBarsPadding().horizontalSafePadding()
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
    TopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = "返回")
                }
            }
        },
        actions = {
            if (isErrorOverviewActive) {
                IconButton(onClick = onExportBugReportClick, enabled = !isExportingBugReport) {
                    Icon(MiuixIcons.Share, contentDescription = "导出并分享异常报告")
                }
            } else if (onSearchRequested != null) {
                IconButton(onClick = onSearchRequested) {
                    Icon(MiuixIcons.Search, contentDescription = "搜索")
                }
            }
        },
    )
}
