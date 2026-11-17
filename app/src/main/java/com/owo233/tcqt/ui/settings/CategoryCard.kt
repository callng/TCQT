package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

@Composable
internal fun FeatureSummaryCard(
    enabledCount: Int,
    disabledCount: Int,
    errorCount: Int,
    onErrorClick: () -> Unit,
) {
    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
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
private fun HeaderFeatureStat(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
            textAlign = TextAlign.Center,
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
