package com.owo233.tcqt.features.appearance.liquidglass

import android.view.View
import android.view.ViewGroup
import android.widget.TabWidget
import androidx.core.view.children

/** Common structural operations; snapshots own restoration. */
internal object TabViewOperations {
    fun findBackdrop(parent: ViewGroup, tab: View): ViewGroup? = parent.children
        .filterIsInstance<ViewGroup>()
        .filter { it !== tab && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 && !QQTabLocator.isBlurWrapper(it) }
        .maxByOrNull { it.width.toLong() * it.height }

    fun disableTabWidgetStrips(tab: View) {
        if (tab is TabWidget) { tab.isStripEnabled = false; tab.dividerDrawable = null }
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
