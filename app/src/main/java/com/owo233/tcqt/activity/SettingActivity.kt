@file:OptIn(ExperimentalFoundationApi::class)

package com.owo233.tcqt.activity

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.generated.GeneratedFeaturesData
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.hooks.func.ModuleCommand
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.MMKVUtils
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.log.Log
import kotlinx.coroutines.launch

class SettingActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingTheme(darkTheme = isDarkTheme) {
                val viewModel: SettingViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                var restartPrompt by remember { mutableStateOf<RestartPrompt?>(null) }
                var showClearDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = viewModel.isSearchActive) {
                    viewModel.exitSearch()
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("清空配置") },
                        text = { Text("是否清空所有模块配置？该操作不可逆，清空后将恢复为默认值。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showClearDialog = false
                                    viewModel.clearAllSettings()
                                    restartPrompt = RestartPrompt.Clear
                                }
                            ) {
                                Text("清空")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("取消")
                            }
                        }
                    )
                }

                if (restartPrompt != null) {
                    val prompt = restartPrompt!!
                    AlertDialog(
                        onDismissRequest = {
                            restartPrompt = null
                            scope.launch {
                                snackbarHostState.showSnackbar(prompt.dismissMessage)
                            }
                        },
                        title = { Text(prompt.title) },
                        text = { Text(prompt.message) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    restartPrompt = null
                                    ModuleCommand.sendCommand(this, "exitApp")
                                }
                            ) {
                                Text("立即重启")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    restartPrompt = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar(prompt.dismissMessage)
                                    }
                                }
                            ) {
                                Text("稍后")
                            }
                        }
                    )
                }

                SettingScreen(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onSearchRequested = viewModel::enterSearch,
                    onSearchClosed = viewModel::exitSearch,
                    onIssueClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, false) },
                    onIssueLongClick = { openUrlInDefaultBrowser(TCQTBuild.OPEN_ISSUES, true) },
                    onSaveClick = {
                        if (!viewModel.hasPendingChanges) {
                            scope.launch {
                                snackbarHostState.showSnackbar("没有修改需要保存")
                            }
                            return@SettingScreen
                        }

                        viewModel.saveChanges()
                        restartPrompt = RestartPrompt.Save
                    },
                    onSaveLongClick = {
                        showClearDialog = true
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkSettingColors else LightSettingColors,
        content = content
    )
}

@Composable
private fun SettingScreen(
    viewModel: SettingViewModel,
    snackbarHostState: SnackbarHostState,
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveLongClick: () -> Unit
) {
    val visibleFeatures by viewModel.visibleFeaturesState
    val hasPending = viewModel.hasPendingChanges

    val pulse = rememberInfiniteTransition(label = "savePulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "savePulseValue"
        ).value

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        },
        floatingActionButton = {
            SavePill(
                hasPendingChanges = hasPending,
                pendingCount = viewModel.pendingChangeCount,
                pulseScale = if (hasPending) pulse else 1f,
                onClick = onSaveClick,
                onLongClick = onSaveLongClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CompactHeaderCard(
                    hostName = PlatformTools.getHostName(),
                    hostVersion = "${PlatformTools.getHostVersion()} (${PlatformTools.getHostVersionCode()}) ${PlatformTools.getHostChannel()}",
                    moduleName = TCQTBuild.APP_NAME,
                    moduleVersion = TCQTBuild.VER_NAME,
                    enabledCount = viewModel.enabledCount,
                    disabledCount = viewModel.disabledCount
                )
            }

            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(bottom = 8.dp)
                ) {
                    ControlCard(
                        tabs = viewModel.tabs,
                        currentTab = viewModel.currentTab,
                        isSearchActive = viewModel.isSearchActive,
                        searchQuery = viewModel.searchQuery,
                        resultCount = visibleFeatures.size,
                        onTabClick = viewModel::selectTab,
                        onSearchClick = onSearchRequested,
                        onSearchBackClick = onSearchClosed,
                        onQueryChange = viewModel::updateSearchQuery,
                        onClearQuery = viewModel::clearSearchQuery
                    )
                }
            }

            if (visibleFeatures.isEmpty()) {
                item {
                    EmptyStateCard()
                }
            } else {
                items(
                    items = visibleFeatures,
                    key = { it.key }
                ) { feature ->
                    FeatureCard(
                        feature = feature,
                        searchQuery = viewModel.searchQuery,
                        enabled = viewModel.isFeatureEnabled(feature.key),
                        expanded = viewModel.isExpanded(feature),
                        hasPending = viewModel.hasPendingFor(feature),
                        onExpandToggle = { viewModel.toggleExpanded(feature.key) },
                        onEnabledChange = { checked -> viewModel.setFeatureEnabled(feature.key, checked) },
                        onOptionChanged = { key, value -> viewModel.setOptionValue(key, value) },
                        onTextChanged = { key, value -> viewModel.setTextValue(key, value) },
                        viewModel = viewModel
                    )
                }
            }

            item {
                FooterCard(
                    onIssueClick = onIssueClick,
                    onIssueLongClick = onIssueLongClick
                )
            }
        }
    }
}

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
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "TCQT 设置中心",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 合并后的宿主环境 + 模块版本卡片，居中显示，左右留边距
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                CombinedInfoCard(
                    hostName = hostName,
                    hostVersion = hostVersion,
                    moduleName = moduleName,
                    moduleVersion = moduleVersion,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 统计卡片行
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

/**
 * 合并后的信息卡片，垂直排列宿主环境和模块版本，居中显示
 */
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
        color = MaterialTheme.colorScheme.secondaryContainer, // 统一背景色
        modifier = modifier.wrapContentWidth() // 宽度包裹内容
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoRow(
                title = "宿主环境",
                value = "$hostName\n$hostVersion"
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 2.dp)
            )
            InfoRow(
                title = "模块版本",
                value = "$moduleName\n$moduleVersion"
            )
        }
    }
}

/**
 * 单行信息（标题 + 值），居中对齐
 */
@Composable
private fun InfoRow(
    title: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
private fun SmallStatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ControlCard(
    tabs: List<String>,
    currentTab: String,
    isSearchActive: Boolean,
    searchQuery: String,
    resultCount: Int,
    onTabClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchBackClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    onBackClick = onSearchBackClick,
                    onClearClick = onClearQuery
                )
                Text(
                    text = "共找到 $resultCount 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "功能配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    CircleActionButton(
                        onClick = onSearchClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    )
                }

                if (tabs.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(tabs, key = { it }) { tab ->
                            FilterChip(
                                selected = currentTab == tab,
                                onClick = { onTabClick(tab) },
                                label = {
                                    Text(
                                        text = tab,
                                        fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleActionButton(
            onClick = onBackClick,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        )

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索功能名称或描述") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (query.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onClearClick)
                    )
                }
            }
        )
    }
}

@Composable
private fun EmptyStateCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 52.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "没有找到相关功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "试试更换关键词，或者切换到其他分类。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FooterCard(
    onIssueClick: () -> Unit,
    onIssueLongClick: () -> Unit
) {
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
            modifier = Modifier
                .combinedClickable(
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
            text = "TCQT Module © 2026",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SavePill(
    hasPendingChanges: Boolean,
    pendingCount: Int,
    pulseScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (hasPendingChanges) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (hasPendingChanges) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shadowElevation = if (hasPendingChanges) 8.dp else 0.dp,
        modifier = Modifier
            .scale(pulseScale)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "保存"
            )
            Text(
                text = if (pendingCount > 0) "保存配置 · $pendingCount" else "保存配置",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeatureCard(
    feature: SettingFeature,
    searchQuery: String,
    enabled: Boolean,
    expanded: Boolean,
    hasPending: Boolean,
    onExpandToggle: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOptionChanged: (String, Int) -> Unit,
    onTextChanged: (String, String) -> Unit,
    viewModel: SettingViewModel
) {
    val query = searchQuery.trim()
    val borderColor = when {
        expanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        hasPending -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (expanded || enabled) 1.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = feature.expandable, onClick = onExpandToggle)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = highlightedText(feature.label, query),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (feature.expandable) {
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Rounded.KeyboardArrowUp
                                } else {
                                    Icons.Rounded.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(
                            text = if (enabled) "已启用" else "未启用",
                            containerColor = if (enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        if (hasPending) {
                            StatusPill(
                                text = "未保存",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (feature.optionGroup != null) {
                            StatusPill(
                                text = "选项",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (feature.textAreas.isNotEmpty()) {
                            StatusPill(
                                text = "文本 ${feature.textAreas.size}",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            AnimatedVisibility(visible = expanded && feature.expandable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (feature.desc.isNotBlank()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        Text(
                            text = highlightedText(feature.desc, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    feature.optionGroup?.let { group ->
                        if (feature.desc.isBlank()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                        val currentValue = viewModel.currentOptionValue(group)
                        OptionGroup(
                            group = group,
                            currentValue = currentValue,
                            onValueChange = { value -> onOptionChanged(group.key, value) }
                        )
                    }

                    feature.textAreas.forEach { area ->
                        val textValue = viewModel.currentTextValue(area.key)
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { onTextChanged(area.key, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(area.label) },
                            placeholder = {
                                Text(area.placeholder.ifBlank { area.label })
                            },
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
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OptionGroup(
    group: FeatureOptionGroup,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        group.options.forEachIndexed { index, option ->
            val mask = group.resolveMask(option, index)
            val selected = if (group.isMulti) {
                (currentValue and mask) != 0
            } else {
                currentValue == option.value
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val nextValue = if (group.isMulti) {
                            currentValue xor mask
                        } else {
                            option.value
                        }
                        onValueChange(nextValue)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                shape = if (group.isMulti) RoundedCornerShape(5.dp) else CircleShape
                            )
                    )

                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightedText(text: String, keyword: String): AnnotatedString {
    // 先获取 colorScheme（在 Composable 上下文中）
    val colorScheme = MaterialTheme.colorScheme
    // 将 colorScheme 作为 remember 的依赖，确保主题变化时重新计算
    return remember(text, keyword, colorScheme) {
        if (keyword.isBlank() || text.isBlank()) {
            return@remember AnnotatedString(text)
        }

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

                if (index > cursor) {
                    append(text.substring(cursor, index))
                }

                pushStyle(
                    SpanStyle(
                        color = highlightColor,
                        background = highlightBackground,
                        fontWeight = FontWeight.Bold
                    )
                )
                append(text.substring(index, index + keyword.length))
                pop()

                cursor = index + keyword.length
            }
        }
    }
}

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

    private val _visibleFeatures = mutableStateOf<List<SettingFeature>>(emptyList())
    val visibleFeaturesState: State<List<SettingFeature>> = _visibleFeatures

    init {
        reloadPersistedSettings()
        refreshStats()
        updateVisibleFeatures()
    }

    private fun updateVisibleFeatures() {
        _visibleFeatures.value = computeVisibleFeatures()
    }

    private fun computeVisibleFeatures(): List<SettingFeature> {
        val keyword = searchQuery.trim().lowercase()

        if (keyword.isBlank()) {
            return if (currentTab.isBlank()) {
                allFeatures
            } else {
                allFeatures.filter { it.tab == currentTab }
            }
        }

        return allFeatures
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

    fun enterSearch() {
        isSearchActive = true
    }

    fun exitSearch() {
        isSearchActive = false
        searchQuery = ""
        updateVisibleFeatures()
    }

    fun updateSearchQuery(value: String) {
        searchQuery = value
        updateVisibleFeatures()
    }

    fun clearSearchQuery() {
        searchQuery = ""
        updateVisibleFeatures()
    }

    fun selectTab(tab: String) {
        currentTab = tab
        if (isSearchActive) {
            exitSearch()
        } else {
            updateVisibleFeatures()
        }
    }

    fun toggleExpanded(key: String) {
        expandedKeys[key] = !(expandedKeys[key] ?: false)
    }

    fun isExpanded(feature: SettingFeature): Boolean {
        return if (searchQuery.isNotBlank()) {
            feature.expandable
        } else {
            expandedKeys[feature.key] == true
        }
    }

    fun hasPendingFor(feature: SettingFeature): Boolean {
        if (pendingBooleans.containsKey(feature.key)) return true
        if (feature.optionGroup != null && pendingInts.containsKey(feature.optionGroup.key)) return true
        return feature.textAreas.any { pendingStrings.containsKey(it.key) }
    }

    fun isFeatureEnabled(key: String): Boolean {
        return pendingBooleans[key] ?: persistedBooleans[key] ?: false
    }

    fun currentOptionValue(group: FeatureOptionGroup): Int {
        val baseValue = pendingInts[group.key] ?: persistedInts[group.key] ?: group.fallbackValue
        return if (group.isMulti) {
            baseValue
        } else {
            group.options.firstOrNull { it.value == baseValue }?.value ?: group.fallbackValue
        }
    }

    fun currentTextValue(key: String): String {
        return pendingStrings[key] ?: persistedStrings[key].orEmpty()
    }

    fun setFeatureEnabled(key: String, value: Boolean) {
        val persisted = persistedBooleans[key] ?: false
        if (value == persisted) {
            pendingBooleans.remove(key)
        } else {
            pendingBooleans[key] = value
        }
        refreshStats()
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
        refreshStats()
    }

    fun clearAllSettings() {
        MMKVUtils.mmkvWithId(TCQTBuild.APP_NAME).clearAll()

        pendingBooleans.clear()
        pendingInts.clear()
        pendingStrings.clear()
        expandedKeys.clear()

        reloadPersistedSettings()
        refreshStats()
        updateVisibleFeatures()
    }

    private fun reloadPersistedSettings() {
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

    private fun refreshStats() {
        val enabled = allFeatures.count { feature ->
            pendingBooleans[feature.key] ?: persistedBooleans[feature.key] ?: false
        }
        enabledCount = enabled
        disabledCount = (allFeatures.size - enabled).coerceAtLeast(0)
    }

    private fun tabPriority(tab: String): Int {
        return when (tab) {
            "基础" -> 0
            "杂项" -> 100
            "调试" -> 101
            else   -> 1
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

private fun openUrlInDefaultBrowser(url: String, isSkip: Boolean) {
    if (!openTelegramChannel(isSkip)) {
        runCatching {
            if (!url.contains(TCQTBuild.OPEN_SOURCE)) {
                Toasts.error("尝试打开不支持的链接!")
                Log.e("尝试打开不受支持的链接: $url !!!")
                return
            }
            val uri = url.toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            HookEnv.hostAppContext.startActivity(intent)
        }.onFailure { _ ->
            Toasts.error("Failed to open url: $url")
            HookEnv.hostAppContext.copyToClipboard(url, false)
            Toasts.info("Url地址已复制到剪贴板,请手动访问.")
        }
    }
}

private fun openTelegramChannel(isSkip: Boolean): Boolean {
    if (isSkip) return false

    val tgIntent = Intent(Intent.ACTION_VIEW).apply {
        data = "tg://resolve?domain=${TCQTBuild.TG_GROUP}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        HookEnv.hostAppContext.startActivity(tgIntent)
        return true
    } catch (_: ActivityNotFoundException) {
        return false
    } catch (_: Exception) {
        return false
    }
}

@Stable
data class SettingFeature(
    val key: String,
    val label: String,
    val desc: String,
    val order: Int,
    val tab: String,
    val textAreas: List<TextAreaField>,
    val optionGroup: FeatureOptionGroup?
) {
    val expandable: Boolean
        get() = desc.isNotBlank() || textAreas.isNotEmpty() || optionGroup != null

    val labelLower: String = label.lowercase()
    val descLower: String = desc.lowercase()
}

@Stable
data class TextAreaField(
    val key: String,
    val label: String,
    val placeholder: String
)

@Stable
data class FeatureOptionGroup(
    val key: String,
    val isMulti: Boolean,
    val fallbackValue: Int,
    val options: List<OptionItem>
) {
    fun resolveMask(option: OptionItem, index: Int): Int {
        if (!isMulti) return option.value

        val allPowerOfTwo = options.all { value ->
            value.value > 0 && (value.value and (value.value - 1)) == 0
        }

        return if (allPowerOfTwo) {
            option.value
        } else {
            1 shl index
        }
    }
}

@Stable
data class OptionItem(
    val label: String,
    val value: Int
)

private enum class RestartPrompt(
    val title: String,
    val message: String,
    val dismissMessage: String
) {
    Save(
        title = "设置已保存",
        message = "是否现在重启宿主以应用修改？",
        dismissMessage = "设置已保存，重启后生效"
    ),
    Clear(
        title = "配置已清空",
        message = "是否现在重启宿主以应用默认配置？",
        dismissMessage = "配置已清空，重启后生效"
    )
}

private val LightSettingColors = lightColorScheme(
    primary = Color(0xFF2855D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D1B52),
    secondaryContainer = Color(0xFFE6F0FF),
    onSecondaryContainer = Color(0xFF17325F),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF161C28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161C28),
    surfaceVariant = Color(0xFFECEFF5),
    onSurfaceVariant = Color(0xFF5B6576),
    outline = Color(0xFF8D96A7),
    outlineVariant = Color(0xFFD5DBE6),
    error = Color(0xFFBA1A1A)
)

private val DarkSettingColors = darkColorScheme(
    primary = Color(0xFFB5C7FF),
    onPrimary = Color(0xFF0F286B),
    primaryContainer = Color(0xFF243E87),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondaryContainer = Color(0xFF22314A),
    onSecondaryContainer = Color(0xFFD5E4FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE7EAF1),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE7EAF1),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFFC0C7D4),
    outline = Color(0xFF8A92A1),
    outlineVariant = Color(0xFF353C49),
    error = Color(0xFFFFB4AB)
)
