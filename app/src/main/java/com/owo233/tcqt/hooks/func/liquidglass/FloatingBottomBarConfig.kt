package com.owo233.tcqt.hooks.func.liquidglass

import com.owo233.tcqt.internals.setting.TCQTSetting

/** The two bottom-bar implementations exposed in the floating bottom-bar card. */
internal enum class BottomBarImplementation(val storedValue: Int) {
    LASTED(1),
    NEW_VIEW(2),
}

internal enum class FloatingBottomBarMode(val storedValue: Int) {
    NORMAL(1),
    LIQUID_GLASS(2),
}

/** Vertical placement of the floating bar relative to the window bottom.
 * Stored values stay stable; visual mapping follows the labels in settings:
 * 适中 is close to the bottom and 靠底 keeps the moderate gap.
 */
internal enum class FloatingBottomBarPosition(val storedValue: Int) {
    MODERATE(1),
    BOTTOM(2),
}

internal data class FloatingBottomBarConfig(
    val implementation: BottomBarImplementation = BottomBarImplementation.LASTED,
    val mode: FloatingBottomBarMode = FloatingBottomBarMode.NORMAL,
    val scale: Float = 1f,
    val blurPercent: Int = 100,
    val position: FloatingBottomBarPosition = FloatingBottomBarPosition.MODERATE,
)

/**
 * Shared configuration contract for the settings UI and the host process.
 * Missing values deliberately resolve to the old implementation and full blur.
 */
internal object FloatingBottomBarConfigStore {
    const val IMPLEMENTATION_KEY = "liquid_glass_tab_bar.implementation"
    const val MODE_KEY = "liquid_glass_tab_bar.mode"
    const val SCALE_KEY = "liquid_glass_tab_bar.scale"
    const val BLUR_KEY = "liquid_glass_tab_bar.blur_percent"
    const val POSITION_KEY = "liquid_glass_tab_bar.position"

    const val DEFAULT_IMPLEMENTATION = 1
    const val DEFAULT_MODE = 1
    const val DEFAULT_SCALE_PERCENT = 100
    const val DEFAULT_BLUR_PERCENT = 100
    const val DEFAULT_POSITION = 1

    fun read(): FloatingBottomBarConfig {
        val implementation = when (TCQTSetting.getInt(IMPLEMENTATION_KEY)) {
            BottomBarImplementation.NEW_VIEW.storedValue -> BottomBarImplementation.NEW_VIEW
            else -> BottomBarImplementation.LASTED
        }
        val mode = when (TCQTSetting.getInt(MODE_KEY)) {
            FloatingBottomBarMode.LIQUID_GLASS.storedValue -> FloatingBottomBarMode.LIQUID_GLASS
            else -> FloatingBottomBarMode.NORMAL
        }
        val percent = TCQTSetting.getInt(SCALE_KEY).takeIf { it in 80..120 } ?: DEFAULT_SCALE_PERCENT
        val blurPercent = TCQTSetting.getInt(BLUR_KEY).takeIf { it in 0..100 } ?: DEFAULT_BLUR_PERCENT
        val position = when (TCQTSetting.getInt(POSITION_KEY)) {
            FloatingBottomBarPosition.BOTTOM.storedValue -> FloatingBottomBarPosition.BOTTOM
            else -> FloatingBottomBarPosition.MODERATE
        }
        return FloatingBottomBarConfig(
            implementation = implementation,
            mode = mode,
            scale = percent / 100f,
            blurPercent = blurPercent,
            position = position,
        )
    }

    fun percent(scale: Float): Int = (scale.coerceIn(0.8f, 1.2f) * 100f).toInt()
}
