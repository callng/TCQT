package com.owo233.tcqt.features.appearance.liquidglass

import androidx.annotation.RequiresApi
import android.os.Build
import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TabWidget
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import com.owo233.tcqt.R
import com.owo233.tcqt.core.log.Log
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.view.isVisible

internal object ClassicTabContentLayout {
    private const val TAB_BREATHING_DP = 32f
    private const val TAB_MIN_WIDTH_DP = 76f
    private const val SCREEN_MARGIN_DP = 28f
    private const val ICON_ONLY_BASIS_DP = 24f
    private val ICON_TRANSLATION_TAG = R.id.tcqt_tag_icon_translation
    // ---- 尺寸与几何 ----

    /**
     * 把宿主的等分 Tab 列替换为按内容定宽的列。
     *
     * 无界的 UNSPECIFIED 测量可能返回大于已布局槽位的无意义值（MATCH_PARENT 子项在
     * 无界规格下行为未定义），此时弃用该值，改以叶子视图的实际宽度为准；两者取宽者。
     */
    fun hugContentWidth(tabRow: ViewGroup?, density: Float): Int? {
        if (tabRow == null || tabRow.isEmpty()) return null
        val iconOnly = isIconOnlyRow(tabRow)
        alignIconOnlyRow(tabRow, iconOnly, density)

        var count = 0
        var measured = 0
        var leaf = 0
        var slot = 0
        for (tab in tabRow.children) {
            if (tab.isGone) continue
            count++
            tab.measure(unspecifiedSpec, unspecifiedSpec)
            measured = max(measured, tab.measuredWidth)
            leaf = max(leaf, TabViewOperations.leafContentWidth(tab))
            slot = max(slot, tab.width)
        }
        if (count == 0) return null
        if (slot in 1..<measured) measured = 0 // 无界测量失效，弃用

        // 纯图标模式下 MATCH_PARENT 的图标是唯一真实内容，其视图宽度
        // 是旧的整屏槽位而非图标本身；采用稳定基准值，避免药丸随未读数伸缩。
        val widest = if (iconOnly) {
            (density * ICON_ONLY_BASIS_DP).roundToInt()
        } else {
            max(measured, leaf)
        }
        if (widest <= 0) return null

        val horizontalPad = (density * 4f).roundToInt()
        var tabWidth = max(
            (density * TAB_MIN_WIDTH_DP).roundToInt(),
            widest + (density * TAB_BREATHING_DP).roundToInt(),
        )
        val screen = tabRow.resources.displayMetrics.widthPixels
        val maxTotal = screen - (density * SCREEN_MARGIN_DP).roundToInt() * 2
        if (tabWidth * count + horizontalPad * 2 > maxTotal) {
            tabWidth = max(1, (maxTotal - horizontalPad * 2) / count)
        }

        for (tab in tabRow.children) {
            if (tab.isGone) continue
            val lp = tab.layoutParams ?: continue
            lp.width = tabWidth
            (lp as? LinearLayout.LayoutParams)?.weight = 0f
            tab.layoutParams = lp
        }
        // 仅水平内边距：纵向内边距会把 Tab 顶出药丸底部。
        tabRow.setPadding(horizontalPad, 0, horizontalPad, 0)
        return tabWidth * count + horizontalPad * 2
    }

    private val unspecifiedSpec: Int
        get() = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    /**
     * Tab 列内最宽可见叶子的宽度。
     * MATCH_PARENT 的叶子只与所在列同宽，说明不了内容宽度，直接不计入。
     */
    /**
     * 内容对称的底栏高度。
     * 边到边布局下底栏自身高度不可信（导航预留可能以不同途径混入），以图标到列顶的
     * 间距为基准上下对称推算，且只缩小不放大。
     */
    fun contentBarHeight(tabRow: ViewGroup?, fallback: Int): Int {
        if (tabRow == null || fallback <= 0) return fallback
        var top = Int.MAX_VALUE
        var bottom = 0
        for (tab in tabRow.children) {
            if (tab.visibility != View.VISIBLE) continue
            val bounds = intArrayOf(Int.MAX_VALUE, 0)
            collectLeafBounds(tab, 0, bounds)
            if (bounds[0] < bounds[1]) {
                val base = tabRow.top + tab.top
                top = min(top, base + bounds[0])
                bottom = max(bottom, base + bounds[1])
            }
        }
        if (top == Int.MAX_VALUE || bottom <= top) return fallback
        val symmetric = bottom + top
        return if (symmetric > 0 && symmetric < fallback) symmetric else fallback
    }

    /** Tab 列内可见叶子（按累积偏移）的纵向范围。 */
    fun collectLeafBounds(view: View, offset: Int, out: IntArray) {
        if (view.visibility != View.VISIBLE) return
        if (view is ViewGroup) {
            for (child in view.children) collectLeafBounds(child, offset + child.top, out)
            return
        }
        if (view.width <= 0 || view.height <= 0) return
        out[0] = min(out[0], offset)
        out[1] = max(out[1], offset + view.height)
    }

    /** 丢弃停靠底栏为避开手势区预留的底部内边距，返回被丢弃的量。 */
    fun dropNavigationReserve(tabView: View): Int {
        val reserve = tabView.paddingBottom
        if (reserve <= 0) return 0
        tabView.setPadding(tabView.paddingLeft, tabView.paddingTop, tabView.paddingRight, 0)
        return reserve
    }

    // ---- 纯图标布局 ----

    /** 查找 Tab 的真实标题文字，排除未读角标。 */
    fun findTabTitle(view: View): TextView? {
        if (view is TextView) {
            val name = view.javaClass.name
            val nonEmpty = !view.text?.toString()?.trim().isNullOrEmpty()
            val isBadge = name.contains("Badge") || name.contains("RedTouch")
            val tagged = view.tag is CharSequence
            if (nonEmpty && !isBadge && (tagged || name.contains("BlendTextView") || view.background == null)) {
                return view
            }
        }
        if (view is ViewGroup) {
            for (child in view.children) findTabTitle(child)?.let { return it }
        }
        return null
    }

    /** 标题是否仍占据实际布局槽位（被外置开关置零尺寸视为已隐藏）。 */
    fun hasUsableTabTitle(tab: View): Boolean {
        val title = findTabTitle(tab) ?: return false
        if (title.visibility != View.VISIBLE) return false
        val lp = title.layoutParams ?: return true
        return lp.width != 0 && lp.height != 0
    }

    /** 图标俱在而所有 Tab 均无可用标题时，视为纯图标布局。 */
    fun isIconOnlyRow(tabRow: ViewGroup?): Boolean {
        if (tabRow == null) return false
        var icons = 0
        var titles = 0
        for (tab in tabRow.children) {
            if (tab.isGone || findTabIcon(tab) == null) continue
            icons++
            if (hasUsableTabTitle(tab)) titles++
        }
        return icons > 0 && titles == 0
    }

    fun findTabIcon(view: View): View? {
        if (QQTabLocator.isTabIcon(view)) return view
        if (view is ViewGroup) {
            for (child in view.children) findTabIcon(child)?.let { return it }
        }
        return null
    }

    /** 记录/恢复视图的原始纵偏移，实现可逆的居中补偿。 */
    fun setIconOnlyTranslation(view: View, iconOnly: Boolean, offset: Float) {
        val saved = view.getTag(ICON_TRANSLATION_TAG)
        if (iconOnly) {
            val base =
                saved as? Float ?: view.translationY.also { view.setTag(ICON_TRANSLATION_TAG, it) }
            val desired = base + offset
            if (abs(view.translationY - desired) > 0.5f) view.translationY = desired
        } else if (saved is Float) {
            view.translationY = saved
            view.setTag(ICON_TRANSLATION_TAG, null)
        }
    }

    /**
     * 标题被隐藏后，把图标与角标一并下移到腾出的标题槽位。
     *
     * 仅移动图标与角标本体：若整块容器也随之下移，偏移会被应用两次，
     * 造成各 Tab 高低不齐。
     */
    fun alignIconOnlyContent(view: View, iconOnly: Boolean, offset: Float) {
        val name = view.javaClass.name
        if (QQTabLocator.isTabIcon(view) || name.contains("Badge")) {
            setIconOnlyTranslation(view, iconOnly, offset)
        }
        if (view is ViewGroup) {
            for (child in view.children) alignIconOnlyContent(child, iconOnly, offset)
        }
    }

    fun alignIconOnlyRow(tabRow: ViewGroup, iconOnly: Boolean, density: Float) {
        // 29dp 图标在 5dp 顶边距的 54dp Tab 内，图标中心到 Tab 中心的
        // 精确纵向偏移为 7.5dp。
        val offset = density * 7.5f
        for (tab in tabRow.children) {
            if (tab.visibility != View.GONE) alignIconOnlyContent(tab, iconOnly, offset)
        }
    }

}
