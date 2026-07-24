package com.owo233.tcqt.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Keeps the existing semantic color and typography names while sourcing every value from Miuix.
 * This makes feature UI code readable and prevents Material and Miuix themes from being mixed.
 */
object MaterialTheme {
    val colorScheme: CompatColorScheme
        @Composable
        @ReadOnlyComposable
        get() = CompatColorScheme(MiuixTheme.colorScheme)

    val typography: CompatTypography
        @Composable
        @ReadOnlyComposable
        get() = CompatTypography(MiuixTheme.textStyles)
}

@Stable
class CompatColorScheme(private val colors: Colors) {
    val primary: Color get() = colors.primary
    val onPrimary: Color get() = colors.onPrimary
    val primaryContainer: Color get() = colors.primaryContainer
    val onPrimaryContainer: Color get() = colors.onPrimaryContainer
    val secondary: Color get() = colors.secondary
    val secondaryContainer: Color get() = colors.secondaryContainer
    val onSecondaryContainer: Color get() = colors.onSecondaryContainer
    val background: Color get() = colors.background
    // Existing call sites use `surface` for cards and grouped settings containers.
    // Miuix's own Card defaults to surfaceContainer, which keeps cards lighter than
    // the page in dark mode and avoids the harsh black-on-gray hierarchy.
    val surface: Color get() = colors.surfaceContainer
    val surfaceVariant: Color get() = colors.surfaceVariant
    val onSurface: Color get() = colors.onSurface
    val onSurfaceVariant: Color get() = colors.onSurfaceSecondary
    val outline: Color get() = colors.outline
    val outlineVariant: Color get() = colors.dividerLine
    val error: Color get() = colors.error
    val onError: Color get() = colors.onError
    val errorContainer: Color get() = colors.errorContainer
    val onErrorContainer: Color get() = colors.onErrorContainer
}

@Stable
class CompatTypography(private val styles: TextStyles) {
    val titleLarge: TextStyle get() = styles.title3
    val titleMedium: TextStyle get() = styles.title4
    val titleSmall: TextStyle get() = styles.headline2
    val bodyMedium: TextStyle get() = styles.body2
    val bodySmall: TextStyle get() = styles.footnote1
    val labelLarge: TextStyle get() = styles.button
    val labelMedium: TextStyle get() = styles.footnote1
    val labelSmall: TextStyle get() = styles.footnote2
}

/**
 * Content-slot variant used by existing call sites. Miuix's public TextButton accepts a String,
 * while TCQT also places icons and styled text in text actions.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MiuixButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors(),
        minHeight = 36.dp,
        insideMargin = contentPadding,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.primary,
        ),
        content = content,
    )
}

/**
 * Material-style slot API backed by Miuix WindowDialog, used for existing confirmation flows.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    WindowDialog(
        show = true,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            title?.invoke()
            text?.invoke()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
