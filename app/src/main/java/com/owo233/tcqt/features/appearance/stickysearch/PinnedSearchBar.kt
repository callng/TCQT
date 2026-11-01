package com.owo233.tcqt.features.appearance.stickysearch

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.owo233.tcqt.R
import com.owo233.tcqt.core.log.Log

/** The real QQ control is moved once. Its native listeners, skin and accessibility stay intact. */
internal object PinnedSearchBar {

    private const val SEARCH_CLASS = "com.tencent.mobileqq.widget.search.QUISearchBar"
    private const val CONTACTS_HEADER = "com.tencent.mobileqq.activity.contacts.base.HeaderScrollView"

    fun isNativeSearch(view: View): Boolean = view.javaClass.name == SEARCH_CLASS

    fun findSearch(root: View): View? {
        if (isNativeSearch(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findSearch(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    fun messageSlot(search: View): View {
        (search.getTag(R.id.tcqt_tag_pinned_search) as? Binding)?.let { return it.slot }
        return Binding(search, Kind.MESSAGE).also { it.waitForSlot() }.slot
    }

    fun contacts(search: View) = bind(search, Kind.CONTACTS)

    private fun bind(search: View, kind: Kind) {
        if (search.getTag(R.id.tcqt_tag_pinned_search) is Binding) return
        search.visibility = View.VISIBLE
        Binding(search, kind).waitForSearch()
    }

    private enum class Kind { MESSAGE, CONTACTS }

    // Bindings are owned by their views, never a process-global map of Activities.
    // Detaching a page unregisters its observer; reattaching resumes maintenance.
    private class Binding(private val search: View, private val kind: Kind) {
        val slot = FrameLayout(search.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setTag(R.id.tcqt_tag_pinned_search, this@Binding)
        }
        private val searchParams = search.layoutParams
        private var source: ViewGroup? = null
        private var sourceIndex = -1
        private var scroller: ViewGroup? = null
        private var owner: ViewGroup? = null
        private var panel: FrameLayout? = null
        private var observer: ViewTreeObserver? = null
        private var addedHeight = 0
        private var baseTop = 0
        private var appliedTop = -1
        private var measuringWidth = -1
        private var stopped = false
        private var updatePending = false
        private val layoutLocation = IntArray(2)
        private val update = Runnable {
            updatePending = false
            if (!stopped) maintainSafely()
        }
        private val globalLayout = ViewTreeObserver.OnGlobalLayoutListener { scheduleUpdate() }
        private val miniAppTransition = ViewTreeObserver.OnPreDrawListener {
            // A positive translation opens QQ's mini-app drawer. Follow it with
            // a render property so its own search stays reachable once expanded.
            // Negative bottom overscroll must never push the pinned bar off-screen.
            val frame = panel
            val offset = scroller?.translationY?.coerceAtLeast(0f) ?: 0f
            if (frame != null && frame.translationY != offset) frame.translationY = offset
            true
        }
        private val ownerAttach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = observe()
            override fun onViewDetachedFromWindow(v: View) = stopObserving()
        }

        init { search.setTag(R.id.tcqt_tag_pinned_search, this) }

        fun waitForSlot() = whenAttached(slot) { installFrom(slot) }
        fun waitForSearch() = whenAttached(search) { installFrom(search) }

        private fun whenAttached(view: View, action: () -> Unit) {
            val listener = object : View.OnAttachStateChangeListener {
                fun schedule() {
                    view.post {
                        if (!stopped && panel == null) {
                            runCatching(action).onFailure {
                                Log.e("搜索框吸顶安装失败: $kind", it)
                                restore()
                            }
                        }
                        if (stopped || panel != null) view.removeOnAttachStateChangeListener(this)
                    }
                }
                override fun onViewAttachedToWindow(v: View) = schedule()
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            view.addOnAttachStateChangeListener(listener)
            if (view.isAttachedToWindow) listener.schedule()
        }

        private fun installFrom(anchor: View) {
            if (stopped || !anchor.isAttachedToWindow) return
            var ancestor = anchor.parent as? ViewGroup
            while (ancestor != null) {
                if ((kind == Kind.CONTACTS && ancestor.javaClass.name == CONTACTS_HEADER) ||
                    (kind != Kind.CONTACTS && isRecycler(ancestor))) break
                ancestor = ancestor.parent as? ViewGroup
            }
            val scroll = ancestor ?: return
            var parent = scroll.parent as? ViewGroup
            // Skip wrappers with custom child layout rules.
            while (parent != null && parent !is FrameLayout && parent !is RelativeLayout) {
                parent = parent.parent as? ViewGroup
            }
            val root = parent ?: return
            if (kind == Kind.CONTACTS && scroll.parent !== root) return
            if (kind != Kind.MESSAGE && scroll.layoutParams !is ViewGroup.MarginLayoutParams) return

            if (kind != Kind.MESSAGE) {
                val oldParent = search.parent as? ViewGroup ?: return
                source = oldParent
                sourceIndex = oldParent.indexOfChild(search)
                oldParent.removeView(search)
                oldParent.addView(slot, sourceIndex, ViewGroup.LayoutParams(searchParams.width, 0))
            }
            scroller = scroll
            owner = root
            baseTop = if (kind == Kind.MESSAGE) scroll.paddingTop
                else (scroll.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            val frame = FrameLayout(search.context).apply {
                setTag(R.id.tcqt_tag_pinned_search, this@Binding)
            }
            panel = frame
            (search.parent as? ViewGroup)?.removeView(search)
            frame.addView(search, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                searchParams?.height?.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            root.addView(frame, if (root is RelativeLayout) RelativeLayout.LayoutParams(-1, -2)
                else FrameLayout.LayoutParams(-1, -2))
            check(frame.parent === root) { "Host rejected the pinned search container" }
            root.addOnAttachStateChangeListener(ownerAttach)
            observe()
        }

        private fun observe() {
            stopObserving()
            val root = owner ?: return
            if (!root.isAttachedToWindow || stopped) return
            observer = root.viewTreeObserver.also {
                it.addOnGlobalLayoutListener(globalLayout)
                if (kind == Kind.MESSAGE) it.addOnPreDrawListener(miniAppTransition)
            }
            scheduleUpdate()
        }

        private fun stopObserving() {
            observer?.takeIf { it.isAlive }?.let {
                it.removeOnGlobalLayoutListener(globalLayout)
                it.removeOnPreDrawListener(miniAppTransition)
            }
            observer = null
            owner?.removeCallbacks(update)
            updatePending = false
        }

        private fun scheduleUpdate() {
            val root = owner ?: return
            if (stopped || updatePending || !root.isAttachedToWindow) return
            updatePending = true
            root.post(update)
        }

        private fun maintainSafely() {
            try {
                maintain()
            } catch (error: Throwable) {
                Log.e("搜索框吸顶布局失败: $kind", error)
                restore()
            }
        }

        private fun maintain() {
            val scroll = scroller ?: return
            val root = owner ?: return
            val frame = panel ?: return
            if (scroll.width <= 0) return
            if (measuringWidth != scroll.width || addedHeight == 0 || search.isLayoutRequested) {
                search.measure(View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY),
                    searchParams?.height?.takeIf { it > 0 }?.let {
                        View.MeasureSpec.makeMeasureSpec(it, View.MeasureSpec.EXACTLY)
                    } ?: View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                measuringWidth = scroll.width
                addedHeight = search.measuredHeight
            }
            if (addedHeight <= 0) return
            val currentTop = if (kind == Kind.MESSAGE) scroll.paddingTop
                else (scroll.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            if (appliedTop >= 0 && currentTop != appliedTop) baseTop = currentTop
            val desiredTop = baseTop + addedHeight
            if (currentTop != desiredTop) {
                if (kind == Kind.MESSAGE) {
                    scroll.setPadding(scroll.paddingLeft, desiredTop, scroll.paddingRight, scroll.paddingBottom)
                } else {
                    val params = scroll.layoutParams as ViewGroup.MarginLayoutParams
                    params.topMargin = desiredTop
                    scroll.layoutParams = params
                }
            }
            appliedTop = desiredTop
            // Window coordinates include RecyclerView's transient overscroll /
            // mini-app translation. Use laid-out bounds instead: the panel must
            // not follow that transform or request a new layout on every drag frame.
            layoutOffset(scroll, root, layoutLocation)
            val x = layoutLocation[0]
            // Margin mode moves the whole scroll container down; padding mode
            // leaves it full-screen and clips rows below QQ's existing top padding.
            val y = layoutLocation[1] +
                if (kind == Kind.MESSAGE) baseTop else -addedHeight
            val params = frame.layoutParams as ViewGroup.MarginLayoutParams
            if (params.width != scroll.width || params.height != addedHeight ||
                params.leftMargin != x || params.topMargin != y) {
                params.width = scroll.width
                params.height = addedHeight
                params.leftMargin = x
                params.topMargin = y
                frame.layoutParams = params
            }
        }

        private fun layoutOffset(view: View, root: ViewGroup, out: IntArray) {
            out[0] = 0
            out[1] = 0
            var child = view
            while (child !== root) {
                out[0] += child.left
                out[1] += child.top
                val parent = checkNotNull(child.parent as? View) { "Search scroller left its owner" }
                out[0] -= parent.scrollX
                out[1] -= parent.scrollY
                child = parent
            }
        }

        fun restore() {
            if (stopped) return
            stopped = true
            stopObserving()
            owner?.removeOnAttachStateChangeListener(ownerAttach)
            scroller?.let { scroll ->
                if (kind == Kind.MESSAGE && scroll.paddingTop == appliedTop) {
                    scroll.setPadding(scroll.paddingLeft, baseTop, scroll.paddingRight, scroll.paddingBottom)
                } else if (kind != Kind.MESSAGE) {
                    (scroll.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                        if (params.topMargin == appliedTop) {
                            params.topMargin = baseTop
                            scroll.layoutParams = params
                        }
                    }
                }
            }
            panel?.removeView(search)
            panel?.let { (it.parent as? ViewGroup)?.removeView(it) }
            source?.let { parent ->
                val index = parent.indexOfChild(slot).takeIf { it >= 0 } ?: sourceIndex
                parent.removeView(slot)
                parent.addView(search, index.coerceIn(0, parent.childCount), searchParams)
            }
            if (kind == Kind.MESSAGE && search.parent == null) {
                slot.layoutParams = slot.layoutParams.apply {
                    height = searchParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
                }
                slot.addView(search, FrameLayout.LayoutParams(-1, -2))
            }
            search.setTag(R.id.tcqt_tag_pinned_search, null)
            slot.setTag(R.id.tcqt_tag_pinned_search, null)
        }

        private fun isRecycler(view: View): Boolean {
            var type: Class<*>? = view.javaClass
            while (type != null) {
                if (type.name == "androidx.recyclerview.widget.RecyclerView" ||
                    type.name == "android.support.v7.widget.RecyclerView") return true
                type = type.superclass
            }
            return false
        }
    }
}
