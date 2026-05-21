package com.owo233.tcqt.activity

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkSettingColors
        else -> LightSettingColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

val LightSettingColors = lightColorScheme(
    primary = Color(0xFF2855D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D1B52),
    secondaryContainer = Color(0xFFE6F0FF),
    onSecondaryContainer = Color(0xFF17325F),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF161C28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161C28),
    surfaceVariant = Color(0xFFECEFF5),
    onSurfaceVariant = Color(0xFF5B6576),
    outline = Color(0xFF8D96A7),
    outlineVariant = Color(0xFFD5DBE6),
    error = Color(0xFFBA1A1A)
)

val DarkSettingColors = darkColorScheme(
    primary = Color(0xFFB5C7FF),
    onPrimary = Color(0xFF0F286B),
    primaryContainer = Color(0xFF243E87),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondaryContainer = Color(0xFF22314A),
    onSecondaryContainer = Color(0xFFD5E4FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE7EAF1),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE7EAF1),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFFC0C7D4),
    outline = Color(0xFF8A92A1),
    outlineVariant = Color(0xFF353C49),
    error = Color(0xFFFFB4AB)
)
