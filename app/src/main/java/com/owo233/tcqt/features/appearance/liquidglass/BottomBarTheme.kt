package com.owo233.tcqt.features.appearance.liquidglass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.view.children
import androidx.core.view.isVisible
import com.owo233.tcqt.core.env.HookEnv

/** Native colors are read before removing QQ's backgrounds, once per installation. */
internal data class BottomBarTheme(val dark: Boolean, val surface: Int, val foreground: Int) {
    val outline: Int get() = ColorUtils.setAlphaComponent(foreground, if (dark) 36 else 24)
    val indicator: Int get() = ColorUtils.blendARGB(surface, foreground, if (dark) 0.16f else 0.08f)

    companion object {
        fun default(dark: Boolean) = BottomBarTheme(
            dark,
            if (dark) Color.rgb(43, 43, 46) else Color.rgb(246, 246, 250),
            if (dark) Color.rgb(208, 208, 213) else Color.rgb(95, 98, 107),
        )

        private fun isLight(color: Int): Boolean =
            (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f) > 128f

        fun darkFromTabs(bar: ViewGroup?): Boolean? = foregroundFromTabs(bar)?.let(::isLight)

        private fun foregroundFromTabs(bar: ViewGroup?): Int? {
            val row = bar?.let(QQTabLocator::findTabRow) ?: return null
            // Read one title per unselected tab; badges and selected accent colors do not vote.
            return row.children.filter { it.isVisible && !it.isSelected }
                .mapNotNull { NewViewTabContentLayout.findTitleCandidate(it) as? TextView }
                .filter { it.isVisible }
                .map { it.currentTextColor }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }

        fun fromNative(bar: ViewGroup): BottomBarTheme {
            val foreground = foregroundFromTabs(bar)
            var sampled: Int? = null
            val background = bar.background
            if (background != null) {
                val bounds = Rect(background.bounds)
                val pixel = createBitmap(1, 1)
                try {
                    background.setBounds(0, 0, bar.width, bar.height)
                    val canvas = Canvas(pixel)
                    canvas.translate(-bar.width * 0.5f, -bar.height * 0.5f)
                    background.draw(canvas)
                    sampled = pixel[0, 0]
                } finally {
                    background.bounds = bounds
                    pixel.recycle()
                }
            }
            // Icon-only skins have no labels to vote; use their native background instead.
            val dark = foreground?.let(::isLight)
                ?: sampled?.takeIf { Color.alpha(it) >= 128 }?.let { ColorUtils.calculateLuminance(it) < 0.5 }
                ?: HookEnv.isNightMode()
            val fallback = default(dark)
            val surface = sampled?.let { ColorUtils.compositeColors(it, fallback.surface) } ?: fallback.surface
            return BottomBarTheme(dark, surface, foreground ?: fallback.foreground)
        }
    }
}
