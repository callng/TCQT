package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.ui.component.TCQTPanel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FooterCard(onIssueClick: () -> Unit, onIssueLongClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TCQTPanel(
            cornerRadius = 16.dp,
            color = MiuixTheme.colorScheme.surfaceContainer,
            onClick = onIssueClick,
            onLongPress = onIssueLongClick,
        ) {
            Text(
                text = "反馈问题与建议",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        Text(
            text = "${TCQTBuild.APP_NAME} Module © ${TCQTBuild.COPYRIGHT_YEAR}",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.outline,
        )
    }
}

// ───── Save Pill ─────
@Composable
internal fun SavePill(pendingCount: Int, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current

    top.yukonga.miuix.kmp.basic.FloatingActionButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = MiuixIcons.Ok, contentDescription = null)
            Text(text = if (pendingCount > 0) "保存配置 · $pendingCount" else "保存配置")
        }
    }
}
