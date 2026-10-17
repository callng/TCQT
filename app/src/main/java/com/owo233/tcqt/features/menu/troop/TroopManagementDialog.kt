package com.owo233.tcqt.features.menu.troop

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

internal class TroopManagementDialog(
    context: Context,
    private val content: @Composable (onDismiss: () -> Unit) -> Unit,
) : CompatibleComposeDialog(context) {
    @Composable
    override fun DialogContent() {
        OverlayBottomSheet(
            show = isVisible,
            onDismissRequest = ::dismissWithAnimation,
            onDismissFinished = ::dismissImmediately,
            insideMargin = DpSize(0.dp, 0.dp),
        ) {
            Box(
                Modifier.windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Bottom)
                )
            ) {
                content(::dismissWithAnimation)
            }
        }
    }
}
