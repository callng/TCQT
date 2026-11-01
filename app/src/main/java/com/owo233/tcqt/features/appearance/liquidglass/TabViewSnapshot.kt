package com.owo233.tcqt.features.appearance.liquidglass

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TabWidget
import androidx.core.view.children
import com.owo233.tcqt.R
import java.lang.ref.WeakReference

/** Reversible changes to native tabs, separate from either renderer's installation state. */
internal class TabViewSnapshot(tab: ViewGroup, originalAlpha: Float = tab.alpha) {
    private val target = WeakReference(tab)
    private val parent = WeakReference(tab.parent as? ViewGroup)
    val originalIndex = parent.get()?.indexOfChild(tab) ?: -1
    private val states = mutableListOf<ViewState>()
    private val ancestorClips = mutableListOf<Triple<WeakReference<ViewGroup>, Boolean, Boolean>>()

    init {
        fun capture(view: View) {
            states += ViewState(view, if (view === tab) originalAlpha else view.alpha)
            if (view is ViewGroup) view.children.forEach(::capture)
        }
        capture(tab)
        var ancestor = parent.get()
        repeat(12) {
            val current = ancestor ?: return@repeat
            ancestorClips += Triple(WeakReference(current), current.clipChildren, current.clipToPadding)
            ancestor = if (current.id == android.R.id.content) null else current.parent as? ViewGroup
        }
    }

    fun originalHeight(view: View): Int? = states.firstOrNull { it.view.get() === view }?.height

    fun restore() {
        val tab = target.get() ?: return
        val originalParent = parent.get() ?: return
        if (tab.parent !== originalParent) {
            (tab.parent as? ViewGroup)?.removeView(tab)
            originalParent.addView(tab, originalIndex.coerceIn(0, originalParent.childCount), states.first().params)
        }
        states.forEach { it.restore() }
        ancestorClips.forEach { (ref, children, padding) ->
            ref.get()?.let { it.clipChildren = children; it.clipToPadding = padding }
        }
    }

    private class ViewState(target: View, private val alpha: Float) {
        val view = WeakReference(target)
        val params = target.layoutParams
        private val width = params?.width ?: 0
        val height = params?.height ?: 0
        private val weight = (params as? LinearLayout.LayoutParams)?.weight
        private val margins = (params as? ViewGroup.MarginLayoutParams)?.let { intArrayOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
        private val layoutGravity = (params as? FrameLayout.LayoutParams)?.gravity
        private val padding = intArrayOf(target.paddingLeft, target.paddingTop, target.paddingRight, target.paddingBottom)
        private val background: Drawable? = target.background
        private val translation = target.translationY
        private val gravity = (target as? LinearLayout)?.gravity
        private val clipChildren = (target as? ViewGroup)?.clipChildren
        private val clipPadding = (target as? ViewGroup)?.clipToPadding
        private val strip = (target as? TabWidget)?.isStripEnabled
        private val divider = (target as? TabWidget)?.dividerDrawable

        fun restore() {
            val target = view.get() ?: return
            params?.let {
                it.width = width; it.height = height
                weight?.let { value -> (it as LinearLayout.LayoutParams).weight = value }
                margins?.let { value -> (it as ViewGroup.MarginLayoutParams).setMargins(value[0], value[1], value[2], value[3]) }
                layoutGravity?.let { value -> (it as FrameLayout.LayoutParams).gravity = value }
                target.layoutParams = it
            }
            target.setPadding(padding[0], padding[1], padding[2], padding[3])
            target.background = background
            target.alpha = alpha
            target.translationY = translation
            target.setTag(R.id.tcqt_tag_content_translation, null)
            target.setTag(R.id.tcqt_tag_icon_translation, null)
            if (target is LinearLayout && gravity != null) target.gravity = gravity
            if (target is ViewGroup) { target.clipChildren = clipChildren!!; target.clipToPadding = clipPadding!! }
            if (target is TabWidget) { target.isStripEnabled = strip!!; target.dividerDrawable = divider }
        }
    }
}
