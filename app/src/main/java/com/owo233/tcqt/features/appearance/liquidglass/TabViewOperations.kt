package com.owo233.tcqt.features.appearance.liquidglass

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TabWidget
import androidx.core.view.children
import kotlin.math.max
import androidx.core.view.isVisible

/** Common structural operations; snapshots own restoration. */
internal object TabViewOperations {
    private val location = IntArray(2)

    fun activityOf(view: View): Activity? {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    /** Keep the wrapper measured while QQ hides its child, including the touch target. */
    fun followNativeBar(host: ViewGroup, tab: View, surface: View?, indicator: View?, indicatorBaseY: Float = 0f): Boolean {
        val visible = tab.isVisible && tab.alpha > 0f
        host.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        // QQ's docked tab travels by its height; include the floating gap as well.
        var extraTranslation = 0f
        if (tab.translationY > 0f && tab.height > 0) {
            host.getLocationOnScreen(location)
            val pillTop = location[1] - host.translationY + host.paddingTop
            val root = host.rootView
            root.getLocationOnScreen(location)
            val travel = location[1] + root.height - pillTop
            extraTranslation = max(0f, travel / tab.height - 1f) * tab.translationY
        }
        host.translationY = extraTranslation
        surface?.translationY = tab.translationY
        surface?.alpha = tab.alpha
        indicator?.translationY = indicatorBaseY + tab.translationY
        indicator?.alpha = tab.alpha
        return visible
    }

    fun findBackdrop(parent: ViewGroup, tab: View): ViewGroup? = parent.children
        .filterIsInstance<ViewGroup>()
        .filter { it !== tab && it.isVisible && it.width > 0 && it.height > 0 && !QQTabLocator.isBlurWrapper(it) }
        .maxByOrNull { it.width.toLong() * it.height }

    fun disableTabWidgetStrips(tab: View) {
        if (tab is TabWidget) { tab.isStripEnabled = false; tab.dividerDrawable = null }
    }

    fun stripBarBackgrounds(view: View) {
        val name = view.javaClass.name
        if (name.contains("Badge") || name.contains("RedTouch") || QQTabLocator.isTabIcon(view) || view is android.widget.ImageView) return
        view.background = null
        if (view is ViewGroup) view.children.forEach(::stripBarBackgrounds)
    }

    fun unclipAncestors(from: ViewGroup) {
        var current: ViewGroup? = from
        repeat(12) {
            val group = current ?: return
            group.clipChildren = false; group.clipToPadding = false
            if (group.id == android.R.id.content) return
            current = group.parent as? ViewGroup
        }
    }

    fun leafContentWidth(view: View): Int = when {
        view.visibility != View.VISIBLE -> 0
        view is ViewGroup -> view.children.maxOfOrNull(::leafContentWidth) ?: 0
        view.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT -> 0
        else -> view.width
    }
}
