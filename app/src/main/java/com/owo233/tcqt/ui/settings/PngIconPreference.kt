package com.owo233.tcqt.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.PngIconCodec
import com.owo233.tcqt.ui.settings.model.TextAreaUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PngIconPreference(
    area: TextAreaUiState,
    importing: Boolean,
    onSelect: () -> Unit,
    onReset: () -> Unit,
) {
    val preview by produceState<ImageBitmap?>(null, area.value) {
        value = withContext(Dispatchers.IO) {
            PngIconCodec.decodeOrNull(area.value)?.asImageBitmap()
        }
    }
    Column {
        BasicComponent(
            insideMargin = SettingsRowPadding,
            enabled = !importing,
            onClick = onSelect,
            endActions = {
                preview?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "自定义图标预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(end = 12.dp).size(32.dp),
                    )
                }
                Icon(
                    MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(width = 10.dp, height = 16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        ) {
            Text(area.label, style = MiuixTheme.textStyles.headline1)
            Text(
                when {
                    importing -> "正在读取 PNG…"
                    area.value.isEmpty() -> "QQ 默认图标"
                    else -> "PNG 图片"
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        if (area.value.isNotEmpty()) {
            ArrowPreference(
                title = "恢复默认图标",
                insideMargin = SettingsRowPadding,
                enabled = !importing,
                onClick = onReset,
            )
        }
    }
}
