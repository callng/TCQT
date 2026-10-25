package com.owo233.tcqt.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.ui.component.TCQTPanel
import com.owo233.tcqt.ui.settings.model.CategoryUiState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CategoryCard(category: CategoryUiState, onClick: () -> Unit) {

    ArrowPreference(
        title = category.label,
        summary = "已启用 ${category.enabledCount} · 共 ${category.totalFeatureCount} 项",
        insideMargin = SettingsRowPadding,
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
    isZygisk: Boolean,
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
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(moduleName)
                    if (isZygisk) {
                        withStyle(
                            SpanStyle(
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                fontWeight = FontWeight.Normal,
                            )
                        ) {
                            append(" · 经 Zygisk 注入")
                        }
                    }
                },
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderVersionRow(label = "宿主", version = "$hostName $hostVersion")
                HeaderVersionRow(
                    label = "模块",
                    version = moduleVersion,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val currentTime = System.currentTimeMillis()
                        clickCount = if (currentTime - lastClickTime < 500) clickCount + 1 else 1
                        lastClickTime = currentTime
                        if (clickCount >= 5) {
                            clickCount = 0
                            Toasts.info("编译时间: ${TCQTBuild.BUILD_TIME}")
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderFeatureStat(
                    label = "已启用",
                    count = enabledCount,
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                )
                HeaderFeatureStat(
                    label = "未启用",
                    count = disabledCount,
                    modifier = Modifier.weight(1f),
                )
                HeaderFeatureStat(
                    label = "功能总数",
                    count = enabledCount + disabledCount,
                    modifier = Modifier.weight(1f),
                )
            }

            if (errorCount > 0) {
                ArrowPreference(
                    title = "$errorCount 个功能运行异常",
                    insideMargin = PaddingValues(0.dp),
                    onClick = onErrorClick,
                )
            }
        }
    }
}

@Composable
private fun HeaderVersionRow(
    label: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            softWrap = false,
        )
        Text(
            text = version,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun HeaderFeatureStat(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = count.toString(),
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Medium,
            color = if (highlighted) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
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
                    text = "选择功能查看异常阶段与完整日志",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

// ───── Search Prompt ─────
