package com.owo233.tcqt.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import com.owo233.tcqt.internals.setting.ModuleThemeMode
import com.owo233.tcqt.internals.setting.ThemeSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun SettingTheme(
    themeMode: ModuleThemeMode = ThemeSettings.themeMode,
    monetEnabled: Boolean = ThemeSettings.monetEnabled,
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDark(systemDarkTheme)
    val mode = when {
        monetEnabled && darkTheme -> ColorSchemeMode.MonetDark
        monetEnabled -> ColorSchemeMode.MonetLight
        darkTheme -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.Light
    }
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = mode,
            isDark = darkTheme,
        )
    }
    MiuixTheme(
        controller = controller,
        content = content,
    )
}
