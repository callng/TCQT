package com.owo233.tcqt.features.chat

import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.CompatibleComposeView
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

/** QQ keeps its state and fallback click listener. */
internal class ChatUnreadButtonView(
    private val original: TextView,
    returnToReading: () -> Boolean,
) : FrameLayout(original.context) {

    private var count by mutableStateOf("")
    private var description by mutableStateOf("")

    init {
        id = original.id
        layoutParams = original.layoutParams
        addView(CompatibleComposeView(context, followHostTheme = true) {
            ChatUnreadButton(count, description) {
                if (!returnToReading()) original.performClick()
                syncFromNative()
            }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        syncFromNative()
    }

    fun syncFromNative() {
        count = original.text.toString()
        description = original.contentDescription?.toString().orEmpty()
        visibility = original.visibility
    }
}

@Composable
internal fun ChatUnreadButton(count: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val sink = remember { SinkFeedback() }
    val colors = MiuixTheme.colorScheme
    val arrowColor = colors.onSurfaceContainer.copy(alpha = 0.55f)
    // Include the badge and shadow in the measured bounds so the host need not disable clipping.
    Box(Modifier.padding(6.dp).size(width = 48.dp, height = 58.dp)) {
        Surface(
            onClick = onClick,
            // Compose keeps the clickable target at least 48dp around the smaller visual.
            modifier = Modifier.align(Alignment.BottomCenter).size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                }
                .indication(interaction, sink),
            shape = CircleShape,
            color = colors.surfaceContainer,
            contentColor = arrowColor,
            shadowElevation = 0.5.dp,
            interactionSource = interaction,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).rotate(90f),
                    tint = arrowColor,
                )
            }
        }
        if (count.isNotEmpty()) {
            Badge(
                modifier = Modifier.align(Alignment.TopCenter)
                    .widthIn(min = 22.dp).heightIn(min = 22.dp)
                    .clearAndSetSemantics { },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Text(count, maxLines = 1)
            }
        }
    }
}
