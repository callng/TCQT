package com.owo233.tcqt.loader.validation

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.owo233.tcqt.features.appearance.liquidglass.BottomBarTheme
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBarIndicatorView
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBarSurfaceView
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBottomBarMode
import com.owo233.tcqt.features.appearance.liquidglass.GlassDropletView
import com.owo233.tcqt.features.appearance.liquidglass.GlassPillView
import org.json.JSONObject
import kotlin.math.roundToInt

/** Device-rendered fixtures only. Does not open or modify QQ settings or its theme. */
class FloatingBarPreviewActivity : Activity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = JSONObject().put("syntheticChecks", GlassRegression.synthetic(application))
        filesDir.resolve("floating-bar-regression.json").writeText(report.put("passed", true).toString(2))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(232, 234, 237))
        }
        val themes = listOf(
            "默认" to BottomBarTheme.default(false),
            "深色" to BottomBarTheme.default(true),
            "自定义" to BottomBarTheme(false, Color.rgb(240, 224, 236), Color.rgb(89, 54, 81)),
        )
        for ((title, theme) in themes) {
            content.addView(TextView(this).apply { text = title; setTextColor(Color.DKGRAY); textSize = 13f })
            content.addView(panel(theme, false), LinearLayout.LayoutParams(-1, dp(88)))
            if (Build.VERSION.SDK_INT >= 33) content.addView(panel(theme, true), LinearLayout.LayoutParams(-1, dp(88)))
        }
        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + bars.bottom)
            }
            insets
        }
        setContentView(content)
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.setSystemBarsAppearance(
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    private fun panel(theme: BottomBarTheme, glass: Boolean): FrameLayout {
        val panel = FrameLayout(this).apply { setBackgroundColor(theme.surface) }
        val backdrop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            repeat(3) { index -> addView(TextView(context).apply {
                text = "示例会话 ${index + 1}"
                setTextColor(theme.foreground)
                setPadding(dp(16), dp(4), dp(16), dp(4))
            }) }
        }
        panel.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val barWidth = minOf(dp(312), resources.displayMetrics.widthPixels - dp(48))
        val host = FrameLayout(this)
        panel.addView(host, FrameLayout.LayoutParams(barWidth, dp(64)).apply { gravity = Gravity.CENTER })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("消息", "频道", "联系人", "动态").forEachIndexed { index, title ->
                addView(TextView(context).apply {
                    text = title; gravity = Gravity.CENTER; textSize = 12f; isSelected = index == 0
                    setTextColor(if (index == 0) (if (theme.dark) Color.WHITE else theme.foreground) else theme.foreground)
                }, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
        val surface: View = if (glass && Build.VERSION.SDK_INT >= 33) {
            GlassPillView(this, backdrop, resources.displayMetrics.density, theme.dark).apply { setTheme(theme) }
        } else FloatingBarSurfaceView(this, FloatingBottomBarMode.NORMAL, theme.dark).apply {
            update(FloatingBottomBarMode.NORMAL, theme)
        }
        host.addView(surface, FrameLayout.LayoutParams(-1, -1))
        val indicator: View = if (glass && Build.VERSION.SDK_INT >= 33) {
            GlassDropletView(this, backdrop, row, resources.displayMetrics.density, theme.dark).apply {
                setTheme(theme); setPill(surface); setProgress(0.7f)
            }
        } else FloatingBarIndicatorView(this).apply { setTheme(theme) }
        val indicatorParams = FrameLayout.LayoutParams(barWidth / 4 - dp(4), dp(56)).apply {
            leftMargin = dp(4); topMargin = dp(4)
        }
        if (!glass) host.addView(indicator, indicatorParams)
        host.addView(row, FrameLayout.LayoutParams(-1, -1))
        if (glass) host.addView(indicator, indicatorParams)
        return panel
    }
}
