package com.owo233.tcqt.utils.context

import android.content.Context
import android.content.res.Configuration
import com.tencent.mobileqq.vas.theme.api.ThemeUtil

object HostContextFactory {

    private const val MOCK_THEME_ID = 0

    fun createMaterialContext(base: Context): Context {
        // if (ContextCheckers.isMaterialContext(base)) return base
        return createAppCompatContext(base)
    }

    fun createAppCompatContext(base: Context): Context {
        // if (ContextCheckers.isAppCompatContext(base)) return base
        return HostThemeContext(
            base,
            MOCK_THEME_ID,
            buildNightModeConfig(base)
        )
    }

    private fun buildNightModeConfig(base: Context): Configuration? {
        val night = if (ThemeUtil.isNowThemeIsNight(null, true, null)) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }

        val current = base.resources.configuration
        val mask = current.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (mask == night) return null

        return Configuration(current).apply {
            uiMode =
                night or (current.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
    }
}
