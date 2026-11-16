package com.owo233.tcqt.features.appearance.chatgroups

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.compose.ui.graphics.Color
import com.tencent.mobileqq.utils.QQTheme
import com.owo233.tcqt.R
import com.owo233.tcqt.core.reflect.callMethod

/** A list placeholder reserves the bar's height; the actual bar stays above the scrolling rows. */
internal class ChatGroupHeader(
    val search: View,
    private val bar: ChatGroupBarView,
    private val nativeSearch: View,
) : LinearLayout(search.context) {
    val coveredRows = RectF()
    val pinnedBarHeight: Int get() = bar.measuredHeight
    private val space = View(context)
    private var root: ViewGroup? = null
    private var scroller: ViewGroup? = null
    private var observer: ViewTreeObserver? = null
    private val headerBackgroundId = nativeSearch.resources.getIdentifier(
        "qui_common_bg_nav_primary", "color", nativeSearch.context.packageName,
    )
    private var searchBackground: Drawable? = null
    private var themeApplied = false
    private val bounds = Rect()
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        updatePosition()
        true
    }
    private val rootAttach = object : OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = observe()
        override fun onViewDetachedFromWindow(v: View) = stopObserving()
    }

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(-1, -2)
        (search.parent as? ViewGroup)?.removeView(search)
        addView(search, LayoutParams(-1, search.layoutParams?.height ?: -2))
        addView(space, LayoutParams(-1, 0))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (root != null) return
        post {
            if (!isAttachedToWindow || root != null) return@post
            var ancestor = parent as? ViewGroup
            while (ancestor != null) {
                var type: Class<*>? = ancestor.javaClass
                while (type != null && type.name != "androidx.recyclerview.widget.RecyclerView") {
                    type = type.superclass
                }
                if (type != null) break
                ancestor = ancestor.parent as? ViewGroup
            }
            val scroll = ancestor ?: return@post
            var owner = scroll.parent as? ViewGroup
            while (owner != null && owner !is FrameLayout && owner !is RelativeLayout) {
                owner = owner.parent as? ViewGroup
            }
            val parent = owner ?: return@post
            root = parent
            scroller = scroll
            scroll.setTag(R.id.tcqt_tag_chat_group_header, this)
            parent.addView(bar, if (parent is RelativeLayout) RelativeLayout.LayoutParams(-1, -2)
                else FrameLayout.LayoutParams(-1, -2))
            parent.addOnAttachStateChangeListener(rootAttach)
            observe()
        }
    }

    private fun observe() {
        stopObserving()
        observer = root?.viewTreeObserver?.also { it.addOnPreDrawListener(preDraw) }
    }

    private fun stopObserving() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
    }

    private fun updatePosition() {
        val owner = root ?: return
        val scroll = scroller ?: return
        val height = bar.measuredHeight
        if (height <= 0 || scroll.width <= 0) return
        if (!themeApplied || searchBackground !== nativeSearch.background) {
            if (QQTheme.isVasTheme()) {
                // Leave QQ's skin image visible at its original list coordinates.
                bar.background = null
                val resource = requireNotNull(nativeSearch.callMethod("getCustomResource"))
                val background = resource.callMethod("getInputBackground", nativeSearch) as Drawable
                // This fresh skin drawable is independent of the host View's bounds.
                background.setBounds(0, 0, nativeSearch.width, nativeSearch.height)
                val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(pixel)
                canvas.translate(-background.bounds.exactCenterX(), -background.bounds.exactCenterY())
                background.draw(canvas)
                val surface = Color(pixel.getPixel(0, 0))
                pixel.recycle()
                val fill = if (surface.alpha == 0f) {
                    Color(nativeSearch.context.getColor(headerBackgroundId)).copy(alpha = 0.88f)
                } else surface
                bar.hostColors = ChatGroupHostColors(fill,
                    Color(resource.callMethod("getTextColor", nativeSearch) as Int))
            } else {
                // Default day/night headers have a solid navigation background.
                bar.setBackgroundColor(nativeSearch.context.getColor(headerBackgroundId))
                bar.hostColors = null
            }
            searchBackground = nativeSearch.background
            themeApplied = true
        }
        if (space.layoutParams.height != height) {
            space.layoutParams = space.layoutParams.apply { this.height = height }
        }
        bounds.setEmpty()
        owner.offsetDescendantRectToMyCoords(scroll, bounds)
        val left = bounds.left
        val scrollTop = bounds.top
        val pinnedTop = scrollTop + scroll.paddingTop
        var top = pinnedTop
        // A scrolling search remains above the bar until it leaves the viewport.
        // A pinned search already reserves its height in the RecyclerView's top padding.
        if (space.isAttachedToWindow) {
            bounds.setEmpty()
            owner.offsetDescendantRectToMyCoords(space, bounds)
            top = maxOf(top, bounds.top)
        }
        val params = bar.layoutParams as ViewGroup.MarginLayoutParams
        if (params.width != scroll.width || params.leftMargin != left || params.topMargin != pinnedTop) {
            params.width = scroll.width
            params.leftMargin = left
            params.topMargin = pinnedTop
            bar.layoutParams = params
        }
        bar.translationY = (top - pinnedTop) + scroll.translationY.coerceAtLeast(0f)
        val rowsTop = top - scrollTop + scroll.translationY.coerceAtLeast(0f) - scroll.translationY
        if (coveredRows.top != rowsTop || coveredRows.width() != scroll.width.toFloat() ||
            coveredRows.height() != height.toFloat()) {
            coveredRows.set(0f, rowsTop, scroll.width.toFloat(), rowsTop + height)
            scroll.invalidate()
        }
    }
}
