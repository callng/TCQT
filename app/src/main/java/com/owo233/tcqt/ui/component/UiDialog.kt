package com.owo233.tcqt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val LocalPrimaryAction = staticCompositionLocalOf { false }

/** Slot variant of Miuix Button for actions containing icons or annotated text. */
@Composable
fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.InsideMargin,
    primary: Boolean = LocalPrimaryAction.current,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        insideMargin = contentPadding,
        content = content,
    )
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

/** A bounded body with fixed actions, including on small windows and with the IME open. */
@Composable
fun TCQTAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: String? = null,
    text: (@Composable () -> Unit)? = null,
) {
    WindowDialog(show = true, modifier = modifier, onDismissRequest = onDismissRequest) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Scroll the title with the body when fonts or the keyboard leave little room.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                title?.let {
                    top.yukonga.miuix.kmp.basic.Text(it, style = MiuixTheme.textStyles.title4)
                }
                text?.invoke()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dismissButton?.let { action ->
                    Box(Modifier.weight(1f), propagateMinConstraints = true) { action() }
                }
                Box(Modifier.weight(1f), propagateMinConstraints = true) {
                    CompositionLocalProvider(LocalPrimaryAction provides true) { confirmButton() }
                }
            }
        }
    }
}

/** Overlay in the existing compatible Android window; no second platform window is created. */
@Composable
fun HostDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        maxWidth = UiMetrics.DialogMaxWidth,
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        content()
    }
}

@Composable
fun DialogForm(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title4)
            content()
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = actions,
        )
    }
}
