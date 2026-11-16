package com.owo233.tcqt.ui.component

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.ui.settings.BaseComposeActivity
import com.owo233.tcqt.ui.theme.SettingTheme

/** Full-page counterpart of CompatibleComposeView for feature-owned host screens. */
abstract class CompatibleComposeActivity : BaseComposeActivity() {
    protected fun setHostContent(content: @Composable () -> Unit) {
        setContent {
            SideEffect { updateStatusBarAppearance(isDarkTheme) }
            SettingTheme(themeMode = ModuleThemeMode.System, systemDarkTheme = isDarkTheme, content = content)
        }
    }
}
