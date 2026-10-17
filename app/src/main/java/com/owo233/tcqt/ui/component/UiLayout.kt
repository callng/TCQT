package com.owo233.tcqt.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

object UiMetrics {
    val PageMargin = 12.dp
    val ContentMaxWidth = 800.dp
    val CardRadius = 16.dp
    val DialogMaxWidth = 560.dp
}

/** Keep the scroll surface full width; constrain only its content. */
@Composable
fun AdaptiveContent(content: @Composable (sidePadding: Dp) -> Unit) {
    BoxWithConstraints {
        content(maxOf(UiMetrics.PageMargin, (maxWidth - UiMetrics.ContentMaxWidth) / 2))
    }
}

@Composable
fun Modifier.horizontalSafePadding() =
    windowInsetsPadding(
        WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    )

/** One lazy row in a visually continuous Miuix card. Keys and laziness belong to the caller. */
@Composable
fun PreferenceSegment(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val top = if (first) UiMetrics.CardRadius else 0.dp
    val bottom = if (last) UiMetrics.CardRadius else 0.dp
    Box(
        modifier
            .fillMaxWidth()
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                topStart = top,
                topEnd = top,
                bottomStart = bottom,
                bottomEnd = bottom,
            )
    ) {
        content()
    }
}

/** Miuix card geometry with Box constraints, also suitable for centered icon containers. */
@Composable
fun TCQTPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = UiMetrics.CardRadius,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier.squircleSurface(color, cornerRadius).then(
                if (onClick != null || onLongPress != null) Modifier.combinedClickable(
                    onClick = { onClick?.invoke() }, onLongClick = onLongPress,
                ) else Modifier,
            ),
            propagateMinConstraints = true,
        ) { content() }
    }
}
