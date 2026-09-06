package com.owo233.tcqt.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.hooks.func.liquidglass.BottomBarImplementation
import com.owo233.tcqt.hooks.func.liquidglass.FloatingBottomBarMode
import com.owo233.tcqt.hooks.func.liquidglass.FloatingBottomBarPosition
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun FloatingBottomBarSettingsPanel(
    implementation: Int,
    mode: Int,
    position: Int,
    scalePercent: Int,
    blurPercent: Int,
    onImplementationChange: (Int) -> Unit,
    onModeChange: (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    onScaleChange: (Int) -> Unit,
    onBlurChange: (Int) -> Unit,
) {
    val newView = implementation == BottomBarImplementation.NEW_VIEW.storedValue
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                RadioButtonPreference(
                    title = "Lasted",
                    selected = implementation == BottomBarImplementation.LASTED.storedValue,
                    onClick = { onImplementationChange(BottomBarImplementation.LASTED.storedValue) },
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                )
                RadioButtonPreference(
                    title = "NewView",
                    selected = implementation == BottomBarImplementation.NEW_VIEW.storedValue,
                    onClick = { onImplementationChange(BottomBarImplementation.NEW_VIEW.storedValue) },
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }

        if (newView) {
            Surface(
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "渲染模式",
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    RadioButtonPreference(
                        title = "Normal",
                        selected = mode == FloatingBottomBarMode.NORMAL.storedValue,
                        onClick = { onModeChange(FloatingBottomBarMode.NORMAL.storedValue) },
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    )
                    RadioButtonPreference(
                        title = "Liquid Glass",
                        selected = mode == FloatingBottomBarMode.LIQUID_GLASS.storedValue,
                        onClick = { onModeChange(FloatingBottomBarMode.LIQUID_GLASS.storedValue) },
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
            }

            Surface(
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("悬浮底栏缩放", color = MiuixTheme.colorScheme.onSurface)
                        Text("${scalePercent.coerceIn(80, 120)}%", color = MiuixTheme.colorScheme.primary)
                    }
                    Slider(
                        value = scalePercent.coerceIn(80, 120).toFloat(),
                        onValueChange = { onScaleChange(it.roundToInt()) },
                        valueRange = 80f..120f,
                        showKeyPoints = true,
                        keyPoints = listOf(80f, 90f, 100f, 110f, 120f),
                        magnetThreshold = 1f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                }
            }

            if (mode == FloatingBottomBarMode.LIQUID_GLASS.storedValue) {
                Surface(
                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("背景模糊", color = MiuixTheme.colorScheme.onSurface)
                            Text("${blurPercent.coerceIn(0, 100)}%", color = MiuixTheme.colorScheme.primary)
                        }
                        Slider(
                            value = blurPercent.coerceIn(0, 100).toFloat(),
                            onValueChange = { onBlurChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            showKeyPoints = true,
                            keyPoints = listOf(0f, 25f, 50f, 75f, 100f),
                            magnetThreshold = 1f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        )
                    }
                }
            }
        }

        Surface(
            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = "底栏位置",
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                RadioButtonPreference(
                    title = "适中",
                    selected = position == FloatingBottomBarPosition.MODERATE.storedValue,
                    onClick = { onPositionChange(FloatingBottomBarPosition.MODERATE.storedValue) },
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                )
                RadioButtonPreference(
                    title = "靠底",
                    selected = position == FloatingBottomBarPosition.BOTTOM.storedValue,
                    onClick = { onPositionChange(FloatingBottomBarPosition.BOTTOM.storedValue) },
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }

    }
}
