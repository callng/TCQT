package com.owo233.tcqt.features.appearance.liquidglass

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference

/** Owns only the page properties and listeners changed by the floating bar. */
internal class PageViewChanges {
    private val originals = mutableListOf<Original>()
    private val listeners = mutableListOf<Pair<WeakReference<View>, View.OnLayoutChangeListener>>()

    fun remember(view: View) {
        originals.removeAll { it.view.get() == null }
        if (originals.none { it.view.get() === view }) originals += Original(view)
    }

    fun listen(view: View, listener: View.OnLayoutChangeListener) {
        listeners += WeakReference(view) to listener
        view.addOnLayoutChangeListener(listener)
    }

    fun restore() {
        listeners.forEach { (view, listener) -> view.get()?.removeOnLayoutChangeListener(listener) }
        listeners.clear()
        originals.forEach { it.restore() }
        originals.clear()
    }

    private class Original(target: View) {
        val view = WeakReference(target)
        private val height = target.layoutParams?.height
        private val bottomMargin = (target.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
        private val bottomPadding = target.paddingBottom
        private val clipPadding = (target as? ViewGroup)?.clipToPadding
        private val extendTag = target.getTag(com.owo233.tcqt.R.id.tcqt_tag_extend)
        private val retryTag = target.getTag(com.owo233.tcqt.R.id.tcqt_tag_page_extend_retry)

        fun restore() {
            val target = view.get() ?: return
            target.layoutParams?.let {
                height?.let { original -> it.height = original }
                bottomMargin?.let { original -> (it as? ViewGroup.MarginLayoutParams)?.bottomMargin = original }
                target.layoutParams = it
            }
            target.setPadding(target.paddingLeft, target.paddingTop, target.paddingRight, bottomPadding)
            if (target is ViewGroup && clipPadding != null) target.clipToPadding = clipPadding
            target.setTag(com.owo233.tcqt.R.id.tcqt_tag_extend, extendTag)
            target.setTag(com.owo233.tcqt.R.id.tcqt_tag_page_extend_retry, retryTag)
        }
    }
}
