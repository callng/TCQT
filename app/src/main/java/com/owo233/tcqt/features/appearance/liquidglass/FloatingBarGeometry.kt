package com.owo233.tcqt.features.appearance.liquidglass

import kotlin.math.min
import kotlin.math.roundToInt

/** Logical dimensions shared by the runtime bar and the settings preview. */
internal data class FloatingBarGeometry(
    val totalWidth: Int,
    val barHeight: Int,
    val tabWidth: Int,
    val tabHeight: Int,
    val horizontalPadding: Int,
    val shadowPadding: Int,
    val scale: Float,
) {
    companion object {
        fun fromBase(
            baseBarHeight: Int,
            baseTabWidth: Int,
            baseTabHeight: Int,
            baseShadowPadding: Int,
            tabCount: Int,
            scale: Float,
            maximumWidth: Int = Int.MAX_VALUE,
        ): FloatingBarGeometry {
            val safeScale = scale.coerceIn(0.8f, 1.2f)
            val safeTabCount = tabCount.coerceAtLeast(1)
            val desiredTabWidth = (baseTabWidth * safeScale).roundToInt().coerceAtLeast(1)
            val widthBudget = maximumWidth.coerceAtLeast(safeTabCount + 2)
            val scaledPadding = min((4f * safeScale).roundToInt().coerceAtLeast(1), (widthBudget - safeTabCount) / 2)
            val shadowPadding = min((baseShadowPadding * safeScale).roundToInt().coerceAtLeast(0),
                (widthBudget - safeTabCount - scaledPadding * 2) / 2)
            val scaledTabWidth = min(desiredTabWidth, (widthBudget - shadowPadding * 2 - scaledPadding * 2) / safeTabCount)
            return FloatingBarGeometry(
                // Derive the total from slots instead of scaling it independently:
                // independent rounding leaves a one-pixel remainder on one side.
                totalWidth = scaledTabWidth * safeTabCount + scaledPadding * 2,
                barHeight = (baseBarHeight * safeScale).roundToInt(),
                tabWidth = scaledTabWidth,
                tabHeight = (baseTabHeight * safeScale).roundToInt(),
                horizontalPadding = scaledPadding,
                shadowPadding = shadowPadding,
                scale = safeScale,
            )
        }
    }
}

