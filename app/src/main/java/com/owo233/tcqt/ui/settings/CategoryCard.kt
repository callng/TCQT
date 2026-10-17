package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.ui.component.TCQTPanel
import com.owo233.tcqt.ui.settings.model.CategoryUiState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CategoryCard(category: CategoryUiState, onClick: () -> Unit) {

    ArrowPreference(
        title = category.label,
        summary = "已启用 ${category.enabledCount} · 共 ${category.totalFeatureCount} 项",
        startAction = {
            Surface(
                shape = CircleShape,
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (category.isLeaf) MiuixIcons.Tune else MiuixIcons.Folder,
                        contentDescription = null,
                        tint =
                            if (category.enabledCount > 0) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        onClick = onClick,
    )
}

// ───── Compact Header ─────
@Composable
internal fun CompactHeaderCard(
    hostName: String,
    hostVersion: String,
    moduleName: String,
    moduleVersion: String,
    enabledCount: Int,
    disabledCount: Int,
    errorCount: Int,
    onErrorClick: () -> Unit,
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hostVersion,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$moduleName $moduleVersion",
                        modifier =
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val currentTime = System.currentTimeMillis()
                                clickCount =
                                    if (currentTime - lastClickTime < 500) clickCount + 1 else 1
                                lastClickTime = currentTime
                                if (clickCount >= 5) {
                                    clickCount = 0
                                    Toasts.info("编译时间: ${TCQTBuild.BUILD_TIME}")
                                }
                            },
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.32f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverviewStat(
                    title = "已启用",
                    value = enabledCount,
                    valueColor = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewDivider()
                OverviewStat(
                    title = "未启用",
                    value = disabledCount,
                    valueColor = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OverviewDivider()
                OverviewStat(
                    title = "异常",
                    value = errorCount,
                    valueColor =
                        if (errorCount > 0) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    onClick = if (errorCount > 0) onErrorClick else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun OverviewStat(
    title: String,
    value: Int,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier.then(
                if (onClick != null) {
                    Modifier.squircleClip(12.dp).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
internal fun OverviewDivider() {
    Box(
        modifier =
            Modifier.size(width = 1.dp, height = 30.dp)
                .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.36f))
    )
}

@Composable
internal fun ErrorOverviewHeader(errorCount: Int) {
    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TCQTPanel(
                cornerRadius = 14.dp,
                color = MiuixTheme.colorScheme.errorContainer,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = MiuixIcons.Report,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "$errorCount 个功能存在异常",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.error,
                )
                Text(
                    text = "点击下方功能可展开查看异常阶段与完整日志",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

// ───── Search Prompt ─────
