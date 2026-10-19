package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.AdaptiveContent
import com.owo233.tcqt.ui.component.horizontalSafePadding
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A primary action measured within the page width instead of an unbounded FAB. */
@Composable
internal fun SaveChangesBar(pendingCount: Int, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    AdaptiveContent { sidePadding ->
        Button(
            modifier = Modifier.fillMaxWidth().horizontalSafePadding()
                .padding(horizontal = sidePadding, vertical = 12.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
            minHeight = 52.dp,
            cornerRadius = 16.dp,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "保存更改（$pendingCount）",
                    style = MiuixTheme.textStyles.button,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
