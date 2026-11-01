package com.owo233.tcqt.features.appearance.liquidglass

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TabWidget
import androidx.core.view.children
import androidx.core.view.isGone
import com.owo233.tcqt.R
import com.owo233.tcqt.core.log.Log
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object NewViewTabContentLayout {
    private val CONTENT_TRANSLATION_TAG = R.id.tcqt_tag_content_translation
    private const val TAB_MIN_WIDTH_DP = 76f
    private const val SCREEN_MARGIN_DP = 28f
    private const val ICON_ONLY_BASIS_DP = 24f
    /** Keep the native icon/title group centered after the host's height changes. */
    fun centerTabContent(row: ViewGroup) {
        if (row is LinearLayout) row.gravity = Gravity.CENTER_VERTICAL
        row.children.filter { !it.isGone }.forEach { tab ->
            if (tab is LinearLayout) {
                tab.gravity = Gravity.CENTER
                tab.clipChildren = false
                tab.clipToPadding = false
            }
            if (findIconTitlePair(tab) != null) {
                centerIconTitlePair(tab)
                return@forEach
            }
            val bounds = intArrayOf(Int.MAX_VALUE, 0)
            collectContentBounds(tab, 0, tab.height, bounds)
            if (bounds[0] >= bounds[1] || tab.height <= 0) return@forEach
            val offset = (tab.height - bounds[0] - bounds[1]) * 0.5f
            if (abs(offset) < 0.5f) return@forEach
            if (tab is ViewGroup) {
                tab.children.forEach { child ->
                    val base = child.getTag(CONTENT_TRANSLATION_TAG) as? Float
                        ?: child.translationY.also { child.setTag(CONTENT_TRANSLATION_TAG, it) }
                    child.translationY = base + offset
                }
            }
        }
    }

    fun restoreTranslations(view: View) {
        (view.getTag(CONTENT_TRANSLATION_TAG) as? Float)?.let {
            view.translationY = it
            view.setTag(CONTENT_TRANSLATION_TAG, null)
        }
        if (view is ViewGroup) view.children.forEach(::restoreTranslations)
    }

    /** Re-layout QQ's independently anchored icon and title as one centered group. */
    fun centerIconTitlePair(tab: View) {
        val pair = findIconTitlePair(tab) ?: return
        val icon = pair.first
        val title = pair.second
        val common = icon.parent as? ViewGroup ?: return
        if (title.parent !== common || icon.height <= 0 || title.height <= 0) return
        val gap = (4f * tab.resources.displayMetrics.density).roundToInt()
        val groupHeight = icon.height + gap + title.height
        if (groupHeight > tab.height) return
        val groupTop = (tab.height - groupHeight) * 0.5f
        val commonTop = relativeTop(common, tab)
        val iconBase = icon.getTag(CONTENT_TRANSLATION_TAG) as? Float
            ?: icon.translationY.also { icon.setTag(CONTENT_TRANSLATION_TAG, it) }
        val titleBase = title.getTag(CONTENT_TRANSLATION_TAG) as? Float
            ?: title.translationY.also { title.setTag(CONTENT_TRANSLATION_TAG, it) }
        icon.translationY = iconBase + groupTop - commonTop - icon.top
        title.translationY = titleBase + groupTop + icon.height + gap - commonTop - title.top
    }

    fun relativeTop(view: View, ancestor: View): Int {
        var result = 0
        var current: View? = view
        while (current != null && current !== ancestor) {
            result += current.top
            if (current !== view) result += current.translationY.roundToInt()
            current = current.parent as? View
        }
        return result
    }

    fun findIconTitlePair(view: View): Pair<View, View>? {
        if (view !is ViewGroup) return null
        val icon = view.children.firstOrNull { QQTabLocator.isTabIcon(it) }
            ?: view.children.firstOrNull { it is android.widget.ImageView }
            ?: view.children.firstNotNullOfOrNull(::findIconCandidate)
        val title = view.children.firstOrNull { isTitleCandidate(it) }
            ?: view.children.firstNotNullOfOrNull(::findTitleCandidate)
        if (icon != null && title != null) {
            val iconParent = icon.parent as? ViewGroup
            val titleParent = title.parent as? ViewGroup
            if (iconParent === titleParent) return icon to title
        }
        return view.children.firstNotNullOfOrNull(::findIconTitlePair)
    }

    fun findIconCandidate(view: View): View? =
        if (QQTabLocator.isTabIcon(view)) view
        else if (view is android.widget.ImageView) view
        else (view as? ViewGroup)?.children?.firstNotNullOfOrNull(::findIconCandidate)

    fun findTitleCandidate(view: View): View? =
        if (isTitleCandidate(view)) view else (view as? ViewGroup)?.children?.firstNotNullOfOrNull(::findTitleCandidate)

    fun isTitleCandidate(view: View): Boolean =
        view is android.widget.TextView && !view.text.isNullOrBlank() &&
            // A skin can attach a transparent drawable to the label. Its background
            // is not a reliable distinction from QQ's separate badge widget.
            !view.javaClass.name.contains("Badge", ignoreCase = true)

    fun collectContentBounds(view: View, offset: Int, tabHeight: Int, out: IntArray) {
        if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return
        if (view is ViewGroup) {
            view.children.forEach { child -> collectContentBounds(child, offset + child.top, tabHeight, out) }
            return
        }
        // Ignore full-cell filler layers; they do not describe the icon/title group.
        if (view.height >= tabHeight * 0.9f && view.width >= view.rootView.width * 0.25f) return
        out[0] = min(out[0], offset)
        out[1] = max(out[1], offset + view.height)
    }


    fun hugContentWidth(tabRow: ViewGroup, density: Float): Int? {
        val visible = tabRow.children.filter { !it.isGone }.toList()
        if (visible.isEmpty()) return null
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val iconOnly = visible.all { findTabTitle(it) == null }
        var widest = 0
        visible.forEach { tab ->
            tab.measure(unspecified, unspecified)
            widest = max(widest, if (iconOnly) (ICON_ONLY_BASIS_DP * density).roundToInt() else max(tab.measuredWidth, TabViewOperations.leafContentWidth(tab)))
        }
        val pad = (4f * density).roundToInt()
        val minimumTabWidth = (TAB_MIN_WIDTH_DP * density).roundToInt()
        var tabWidth = max(minimumTabWidth, widest)
        val maxTotal = tabRow.resources.displayMetrics.widthPixels - (SCREEN_MARGIN_DP * density).roundToInt() * 2
        tabWidth = min(tabWidth, max(1, (maxTotal - pad * 2) / visible.size))
        visible.forEach { tab ->
            val lp = tab.layoutParams ?: return@forEach
            lp.width = tabWidth
            (lp as? LinearLayout.LayoutParams)?.weight = 0f
            tab.layoutParams = lp
        }
        tabRow.setPadding(pad, 0, pad, 0)
        return tabWidth * visible.size + pad * 2
    }


    fun findTabTitle(view: View): View? {
        if (isTitleCandidate(view)) return view
        if (view is ViewGroup) view.children.forEach { findTabTitle(it)?.let { found -> return found } }
        return null
    }

    fun contentBarHeight(tabRow: ViewGroup, fallback: Int): Int {
        if (fallback <= 0) return 0
        var top = Int.MAX_VALUE
        var bottom = 0
        tabRow.children.filter { !it.isGone }.forEach { tab ->
            val bounds = intArrayOf(Int.MAX_VALUE, 0)
            collectLeafBounds(tab, 0, bounds)
            if (bounds[0] < bounds[1]) {
                top = min(top, tab.top + bounds[0])
                bottom = max(bottom, tab.top + bounds[1])
            }
        }
        val symmetric = if (top == Int.MAX_VALUE) fallback else bottom + top
        return if (symmetric in 1 until fallback) symmetric else fallback
    }

    fun collectLeafBounds(view: View, offset: Int, out: IntArray) {
        if (view.visibility != View.VISIBLE) return
        if (view is ViewGroup) {
            view.children.forEach { collectLeafBounds(it, offset + it.top, out) }
            return
        }
        if (view.width > 0 && view.height > 0) {
            out[0] = min(out[0], offset)
            out[1] = max(out[1], offset + view.height)
        }
    }

}
