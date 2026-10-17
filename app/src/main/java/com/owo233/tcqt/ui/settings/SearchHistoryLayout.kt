package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.TCQTPanel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchPromptCard() {
    EmptyStateCard(
        icon = MiuixIcons.Search,
        title = "搜索模块功能",
        summary = "输入功能名称或描述",
    )
}

// ───── Search History ─────
@Composable
internal fun SearchHistoryLayout(
    history: List<String>,
    onTagClick: (String) -> Unit,
    onClearClick: () -> Unit,
) {
    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近搜索",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = MiuixIcons.Clear,
                        contentDescription = "清除搜索记录",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { tag ->
                    TCQTPanel(
                        cornerRadius = 12.dp,
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        onClick = { onTagClick(tag) },
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ───── Search Bar ─────
@Composable
internal fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    summary: String? = null,
) {
    TCQTPanel(
        cornerRadius = 16.dp,
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                contentColor = MiuixTheme.colorScheme.primary,
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
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            summary?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
