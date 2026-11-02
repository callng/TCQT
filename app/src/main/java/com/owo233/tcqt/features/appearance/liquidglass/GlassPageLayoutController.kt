package com.owo233.tcqt.features.appearance.liquidglass

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.owo233.tcqt.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal class GlassPageLayoutController(
    private val hostProvider: () -> GlassBarHostLayout?,
    private val pagerProvider: () -> ViewGroup?,
    private val barHeightProvider: () -> Int,
) {
    private val changes = PageViewChanges()
    private var generation = 0
    private val paddedScrollers = ArrayList<WeakReference<ViewGroup>>()
    private var scrollerPad = 0
    private var extentTick = 0
    private val tmpLoc = IntArray(2)
    private val EXTEND_TAG = R.id.tcqt_tag_extend
    private val PAGE_EXTEND_RETRY_TAG = R.id.tcqt_tag_page_extend_retry
    private val MAX_TRACKED_SCROLLERS = 16
    private val PAGE_EXTENT_TICK_FRAMES = 30
    private val PAGE_EXTEND_RETRY_DELAY_MS = 100L
    private val LAST_ROW_GAP_DP = 8f
    fun maintainScrollerPadding() {
        val pad = scrollerPad
        if (pad <= 0) return
        for (i in paddedScrollers.indices) {
            val scroller = paddedScrollers[i].get() ?: continue
            if (scroller.clipToPadding) scroller.clipToPadding = false
            if (scroller.paddingBottom != pad) {
                scroller.setPadding(
                    scroller.paddingLeft, scroller.paddingTop,
                    scroller.paddingRight, pad,
                )
            }
        }
    }

    /** 记录需要逐帧维持留白的滚动容器；重复登记被忽略。 */
    private fun trackPaddedScroller(scroller: ViewGroup) {
        for (i in paddedScrollers.indices) {
            if (paddedScrollers[i].get() === scroller) return
        }
        if (paddedScrollers.size >= MAX_TRACKED_SCROLLERS) paddedScrollers.removeAt(0)
        paddedScrollers.add(WeakReference(scroller))
    }

    /**
     * 按慢速节拍重跑页面拉伸。
     *
     * 宿主每次重新布局都会悄悄重施自己的页面尺寸，且不会在任何我们监听的视图上产生布局
     * 变化。此时页面重新缩回原高，底栏让出的那条带子便露出窗口自身的底色：药丸下方那条
     * 白边，以及列表滑到底部时那块灰白斑块。
     *
     * 本身极廉价：[extendPagesToBottom] 内部每一步都会在自己的缺口已闭合时提前返回，
     * 所以常态下这里只花一次比较；真被改动了也能在半秒内修好，而不必等下一次切页。
     */
    fun maintainPageExtent() {
        val pager = pagerProvider() ?: return
        if (!pager.isAttachedToWindow) return
        if (++extentTick % PAGE_EXTENT_TICK_FRAMES != 0) return
        pager.post { if (pagerProvider() === pager) extendPagesToBottom(pager) }
    }

    // ---- 页面延伸 ----

    /** 把每个页面拉伸至屏幕底部并给滚动容器补充底部留白，使列表末行能滚过药丸下方。 */
    fun extendPagesToBottom(pager: ViewGroup?) {
        if (pager == null || hostProvider() == null) return
        changes.remember(pager)
        if (isPagerMoving(pager)) {
            if (pager.getTag(PAGE_EXTEND_RETRY_TAG) != true) {
                pager.setTag(PAGE_EXTEND_RETRY_TAG, true)
                val token = generation
                pager.postDelayed({
                    if (token != generation || pagerProvider() !== pager) return@postDelayed
                    pager.setTag(PAGE_EXTEND_RETRY_TAG, false)
                    extendPagesToBottom(pager)
                }, PAGE_EXTEND_RETRY_DELAY_MS)
            }
            return
        }
        pager.setTag(PAGE_EXTEND_RETRY_TAG, false)
        val pagerParent = pager.parent as? ViewGroup
        if (pagerParent != null) stretchToBottom(pager, pagerParent.height)
        val target = pager.height
        for (page in pager.children) {
            if (page is ViewGroup) {
                stretchToBottom(page, target)
                extendOnePage(page, target)
            }
        }
    }

    /** 页面滚动期间不改子页尺寸，避免与 ViewPager2 的 attach/measure 竞态。 */
    private fun isPagerMoving(pager: ViewGroup): Boolean = runCatching {
        val state = pager.javaClass.methods.firstOrNull {
            it.name == "getScrollState" && it.parameterTypes.isEmpty()
        }?.invoke(pager) as? Int ?: return@runCatching false
        state != 0
    }.getOrDefault(false)

    private fun extendOnePage(page: ViewGroup, targetHeight: Int) {
        val pageHeight = max(page.height, targetHeight)
        if (pageHeight <= 0) return
        // 以页面自身 padding 形式预留的底栏高度会封顶内容，
        // 丢弃并交给滚动容器的留白接管。
        if (page.paddingBottom > 0) {
            changes.remember(page)
            page.clipToPadding = false
            page.setPadding(page.paddingLeft, page.paddingTop, page.paddingRight, 0)
        }
        for (child in page.children) {
            if (child.visibility != View.VISIBLE || child.height < pageHeight / 2) continue
            stretchToBottom(child, pageHeight)
            keepStretchedToBottom(child)
        }
        padScrollersBottom(page, bottomReserve(page), 0)
    }

    /** 浮动药丸在屏幕底部占据的纵向空间（含末行余量）。 */
    private fun bottomReserve(anchor: View): Int {
        val host = hostProvider() ?: return 0
        if (host.height <= 0) return 0
        host.getLocationOnScreen(tmpLoc)
        val pillTop = tmpLoc[1] - host.translationY + host.paddingTop
        val root = host.rootView ?: return 0
        if (root.height <= 0) return 0
        root.getLocationOnScreen(tmpLoc)
        val density = anchor.resources.displayMetrics.density
        val reserve = tmpLoc[1] + root.height - pillTop + LAST_ROW_GAP_DP * density
        return if (reserve > 0f) reserve.roundToInt() else 0
    }

    private fun stretchToBottom(view: View, pageHeight: Int) {
        if (pageHeight <= 0) return
        if (pageHeight - view.bottom <= 8) return // 已触及底部
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        changes.remember(view)
        var changed = false
        if (lp.bottomMargin != 0) {
            lp.bottomMargin = 0
            changed = true
        }
        if (lp.height >= 0) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            changed = true
        }
        if (changed) view.layoutParams = lp
    }

    /** 页面恢复停靠尺寸时按布局变化重新拉伸。 */
    private fun keepStretchedToBottom(view: View) {
        if (view.getTag(EXTEND_TAG) == true) return
        changes.remember(view)
        view.setTag(EXTEND_TAG, true)
        changes.listen(view, View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val parent = v.parent as? ViewGroup ?: return@OnLayoutChangeListener
            dropParentBottomReserve(parent, v)
            val grand = parent.parent as? ViewGroup
            if (grand != null) stretchToBottom(parent, grand.height)
            stretchToBottom(v, max(parent.height, grand?.height ?: 0))
        })
    }

    /**
     * 丢弃以滚动容器父级 padding 形式存在的底栏预留。
     * 动态页的整高容器带大额底部 padding，MATCH_PARENT 的信息流会停在页内高处；
     * 信息流自身另有滚动留白，该外层预留须与页面级预留一同丢弃。
     */
    private fun dropParentBottomReserve(parent: ViewGroup, child: View) {
        val gap = parent.height - child.bottom
        val reserve = parent.paddingBottom
        val pager = pagerProvider()
        val pageHeight = pager?.height ?: parent.height
        if (child.top > 8 || gap <= 8 || reserve <= 0 ||
            abs(parent.height - pageHeight) > 8 ||
            (barHeightProvider() > 0 && abs(gap - barHeightProvider()) > 8) ||
            abs(reserve - gap) > 8
        ) return
        changes.remember(parent)
        parent.clipToPadding = false
        parent.setPadding(
            parent.paddingLeft, parent.paddingTop, parent.paddingRight,
            max(0, reserve - gap),
        )
    }

    /** 为子树内所有滚动容器补充底部留白并关闭 padding 裁剪，使内容行透过留白带（药丸后方与下方）持续渲染。 */
    private fun padScrollersBottom(root: ViewGroup, pad: Int, depth: Int) {
        if (depth > 12) return
        for (child in root.children) {
            when {
                // 页容器的 RecyclerView 只承载横向翻页，继续下钻，
                // 为页面内真正的滚动容器补充留白。
                isViewPagerRecycler(child) ->
                    padScrollersBottom(child as ViewGroup, pad, depth + 1)

                isScroller(child) -> {
                    val scroller = child as ViewGroup
                    changes.remember(scroller)
                    val parentHeight = root.height
                    if (child.top <= 8 && parentHeight - child.bottom > 8) {
                        dropParentBottomReserve(root, child)
                        stretchToBottom(child, parentHeight)
                        keepStretchedToBottom(child)
                    }
                    // 宿主会反复恢复该开关，逐帧路径上持续维持。
                    if (scroller.clipToPadding) scroller.clipToPadding = false
                    if (scroller.paddingBottom != pad) {
                        scroller.setPadding(
                            scroller.paddingLeft, scroller.paddingTop,
                            scroller.paddingRight, pad,
                        )
                    }
                    if (pad > 0) {
                        scrollerPad = pad
                        trackPaddedScroller(scroller)
                    }
                }

                child is ViewGroup -> padScrollersBottom(child, pad, depth + 1)
            }
        }
    }

    /** 页容器内部承载横向翻页的 RecyclerView。 */
    private fun isViewPagerRecycler(view: View): Boolean {
        if (view !is ViewGroup) return false
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            if (cls.name == "androidx.viewpager2.widget.ViewPager2\$RecyclerViewImpl") return true
            cls = cls.superclass
        }
        return false
    }

    /** 以类名链识别滚动容器，兼容宿主的自定义子类。 */
    private fun isScroller(view: View): Boolean {
        if (view !is ViewGroup) return false
        if (view is android.widget.ScrollView || view is android.widget.AbsListView) return true
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            val name = cls.name
            if (name == "androidx.recyclerview.widget.RecyclerView" ||
                name == "androidx.core.widget.NestedScrollView"
            ) return true
            cls = cls.superclass
        }
        return false
    }

    fun observeBackdrop(backdrop: ViewGroup) {
        val token = generation
        changes.listen(backdrop, View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (token == generation && pagerProvider() === backdrop) extendPagesToBottom(backdrop)
        })
    }
    fun reset() { generation++; changes.restore(); paddedScrollers.clear(); scrollerPad = 0; extentTick = 0 }
}
