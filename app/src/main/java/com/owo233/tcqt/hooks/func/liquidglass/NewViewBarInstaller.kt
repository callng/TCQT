package com.owo233.tcqt.hooks.func.liquidglass

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
import com.owo233.tcqt.utils.log.Log
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * NewView's renderer on top of the same structural replacement used by the classic implementation.
 * The QQ TabView remains the source of all labels, icons, badges and click
 * listeners; this class only changes its parent, geometry and visual layers.
 */
internal object NewViewBarInstaller {
    private const val MAX_INSTALL_ATTEMPTS = 40
    private const val RETRY_DELAY_MS = 250L
    private const val SCREEN_MARGIN_DP = 28f
    private const val TAB_MIN_WIDTH_DP = 76f
    private const val ICON_ONLY_BASIS_DP = 24f
    private const val BASE_SHADOW_DP = 14f
    private const val BAR_HEIGHT_DP = 64f
    private val CONTENT_TRANSLATION_TAG = R.id.tcqt_tag_content_translation

    private var hostRef = WeakReference<FloatingBarHostLayout?>(null)
    private var tabViewRef = WeakReference<ViewGroup?>(null)
    private var tabRowRef = WeakReference<ViewGroup?>(null)
    private var backdropRef = WeakReference<ViewGroup?>(null)
    private var indicatorRef = WeakReference<View?>(null)
    private var surfaceRef = WeakReference<View?>(null)
    private var parentRef = WeakReference<ViewGroup?>(null)
    private var driver: DropletGestureDriver? = null
    private var refreshListener: ViewTreeObserver.OnPreDrawListener? = null

    private var originalIndex = -1
    private var originalTabParams: ViewGroup.LayoutParams? = null
    private var originalTabPadding = intArrayOf(0, 0, 0, 0)
    private var originalTabBackground: Drawable? = null
    private var originalStripEnabled = true
    private var originalDividerDrawable: Drawable? = null
    private var originalTabAlpha = 1f
    private var originalRowPadding = intArrayOf(0, 0, 0, 0)
    private var originalChildren = emptyList<OriginalChildLayout>()
    private val originalBackgrounds = IdentityHashMap<View, Drawable?>()
    private val hiddenChrome = IdentityHashMap<View, Int>()

    private var baseBarHeight = 0
    private var baseTabWidth = 0
    private var appliedScale = 0f
    private var appliedDark: Boolean? = null
    private var installedMode: FloatingBottomBarMode? = null
    private var installedPosition: FloatingBottomBarPosition? = null
    /** Captured before the native TabView is detached; detached views often report zero insets. */
    private var navigationInset = 0
    private var reinstallPosted = false
    /** A geometry change needs one post-layout snap before normal animations resume. */
    private var layoutSyncPending = false

    fun currentPager(): ViewGroup? = backdropRef.get()

    fun scheduleInstall(activity: Activity) {
        val decor = activity.window.decorView
        decor.post { reconcile(activity, decor, 0) }
    }

    fun onTabChanged(tabView: View, index: Int) {
        val host = hostRef.get()
        if (host == null || !host.isAttachedToWindow || tabView.parent !== host) {
            (tabView as? ViewGroup)?.post { scheduleInstallFromView(tabView) }
            return
        }
        val row = tabRowRef.get() ?: return
        host.post {
            if (hostRef.get() !== host || !host.isAttachedToWindow) return@post
            val selected = QQTabLocator.selectedIndex(row)
            val target = selected.takeIf { it >= 0 } ?: QQTabLocator.slotForIndex(row, index)
            if (target >= 0) driver?.animateToIndex(target, false)
        }
    }

    private fun scheduleInstallFromView(tabView: ViewGroup) {
        val activity = (tabView.rootView?.context as? Activity) ?: return
        scheduleInstall(activity)
    }

    private fun reconcile(activity: Activity, decor: View, attempt: Int) {
        val config = FloatingBottomBarConfigStore.read()
        if (config.implementation != BottomBarImplementation.NEW_VIEW || activity.isFinishing || activity.isDestroyed) {
            if (hostRef.get() != null) restore()
            return
        }

        val live = hostRef.get()
        if (live != null && live.isAttachedToWindow && live.rootView === decor.rootView) {
            if (installedMode != config.mode) {
                restore()
            } else {
                applyScale(config.scale, config.position)
                applyPosition(config.position)
                return
            }
        } else if (live != null) {
            restore()
        }

        val tabView = QQTabLocator.locateTabView(decor)
        if (tabView == null || tabView.parent !is ViewGroup) {
            if (attempt < MAX_INSTALL_ATTEMPTS) {
                decor.postDelayed({ reconcile(activity, decor, attempt + 1) }, RETRY_DELAY_MS)
            } else {
                Log.w("NewView 轮询 $MAX_INSTALL_ATTEMPTS 次仍未找到可替换底栏")
            }
            return
        }

        if (!install(activity, tabView)) {
            if (attempt < MAX_INSTALL_ATTEMPTS) {
                decor.postDelayed({ reconcile(activity, decor, attempt + 1) }, RETRY_DELAY_MS)
            } else {
                Log.w("NewView 底栏安装条件未满足，放弃本次安装")
            }
            return
        }
        applyScale(config.scale, config.position)
    }

    private fun install(activity: Activity, tabView: ViewGroup): Boolean {
        val parent = tabView.parent as? ViewGroup ?: return false
        if (parent is FloatingBarHostLayout || parent is GlassBarHostLayout) return false
        val row = QQTabLocator.findTabRow(tabView) ?: return false
        val backdrop = findBackdrop(parent, tabView) ?: return false
        val visibleTabs = row.children.filter { !it.isGone }.toList()
        if (visibleTabs.isEmpty()) return false

        val context = tabView.context
        val density = context.resources.displayMetrics.density
        val targetHeight = (BAR_HEIGHT_DP * density).roundToInt()
        val navigationReserve = tabView.paddingBottom
        navigationInset = max(currentNavigationInset(tabView), navigationReserve)
        val contentHeight = contentBarHeight(row, tabView.height - navigationReserve)
        val resolvedHeight = max(targetHeight, contentHeight)

        originalIndex = parent.indexOfChild(tabView)
        if (originalIndex < 0) return false
        originalTabParams = tabView.layoutParams
        originalTabPadding = intArrayOf(tabView.paddingLeft, tabView.paddingTop, tabView.paddingRight, tabView.paddingBottom)
        originalTabBackground = tabView.background
        (tabView as? TabWidget)?.let {
            originalStripEnabled = it.isStripEnabled
            originalDividerDrawable = it.dividerDrawable
        }
        originalTabAlpha = tabView.alpha
        originalRowPadding = intArrayOf(row.paddingLeft, row.paddingTop, row.paddingRight, row.paddingBottom)
        originalChildren = row.children.map { child ->
            val lp = child.layoutParams
            OriginalChildLayout(child, lp?.width ?: 0, lp?.height ?: 0, (lp as? LinearLayout.LayoutParams)?.weight ?: 0f)
        }.toList()
        originalBackgrounds.clear()
        captureBackgrounds(tabView)
        hiddenChrome.clear()

        val barWidth = hugContentWidth(row, density) ?: return false

        baseBarHeight = resolvedHeight
        val horizontalPad = (4f * density).roundToInt().coerceAtLeast(1)
        baseTabWidth = max(1, (barWidth - horizontalPad * 2) / visibleTabs.size)

        val host = FloatingBarHostLayout(context)
        host.setupShadow()
        appliedDark = host.isDarkTheme
        val config = FloatingBottomBarConfigStore.read()
        val mode = config.mode
        val liquid = mode == FloatingBottomBarMode.LIQUID_GLASS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val surface: View = if (liquid) {
            GlassPillView(context, backdrop, density, host.isDarkTheme).apply {
                setBlurPercent(config.blurPercent)
            }
        } else {
            FloatingBarSurfaceView(context, mode, host.isDarkTheme)
        }
        val indicator: View = if (liquid) {
            GlassDropletView(context, backdrop, row, density, host.isDarkTheme).apply {
                setBlurPercent(config.blurPercent)
                visibility = View.INVISIBLE
            }
        } else {
            FloatingBarIndicatorView(context).apply { setTheme(host.isDarkTheme) }
        }
        val shadow = host.shadowPadding
        val inset = effectiveNavigationInset(tabView)
        val floatOffset = floatingOffset(config.position, inset, density)
        val hostParams = FrameLayout.LayoutParams(barWidth + shadow * 2, resolvedHeight + shadow * 2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = floatOffset - shadow + inset
        }
        val tabParams = FrameLayout.LayoutParams(barWidth, resolvedHeight).apply {
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
        }
        val indicatorParams = FrameLayout.LayoutParams(
            baseTabWidth,
            max(1, resolvedHeight - (8f * density).roundToInt()),
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (4f * density).roundToInt()
            topMargin = (4f * density).roundToInt()
        }

        try {
            parent.removeView(tabView)
            parent.addView(host, originalIndex, hostParams)
            host.addView(surface, 0, FrameLayout.LayoutParams(-1, -1))
            if (liquid) {
                host.addView(tabView, 1, tabParams)
                host.addView(indicator, 2, indicatorParams)
            } else {
                host.addView(indicator, 1, indicatorParams)
                host.addView(tabView, 2, tabParams)
            }

            tabView.alpha = 1f
            tabView.background = null
            if (row === tabView) {
                // TabWidget itself can be the measured row. Keep the
                // horizontal breathing room calculated above; clearing the
                // root padding here makes the rightmost slot touch the pill.
                tabView.setPadding(tabView.paddingLeft, 0, tabView.paddingRight, 0)
            } else {
                tabView.setPadding(0, 0, 0, 0)
            }
            stripSolidBackgrounds(tabView)
            disableTabWidgetStrips(tabView)
            hideOwnBarChrome(parent, tabView)
        } catch (t: Throwable) {
            restoreFailed(parent, tabView, host)
            Log.e("NewView 底栏重新父级化失败", t)
            return false
        }

        unclipAncestors(parent)
        hostRef = WeakReference(host)
        tabViewRef = WeakReference(tabView)
        tabRowRef = WeakReference(row)
        backdropRef = WeakReference(backdrop)
        indicatorRef = WeakReference(indicator)
        surfaceRef = WeakReference(surface)
        parentRef = WeakReference(parent)
        appliedScale = 0f
        installedMode = mode
        installedPosition = config.position
        layoutSyncPending = true
        QQTabLocator.tryHookPager(backdrop)
        val initial = QQTabLocator.currentIndex(tabView).coerceAtLeast(0)
        driver = DropletGestureDriver(indicator, row, density).apply {
            setPill(surface)
            setHost(host)
            setTabRow(row)
            animateToIndex(initial, true)
        }.also { host.setDragHandler(it) }
        (indicator as? GlassDropletView)?.setPill(surface)

        refreshListener?.let { runCatching { host.viewTreeObserver.removeOnPreDrawListener(it) } }
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (hostRef.get() !== host || !host.isAttachedToWindow) return@OnPreDrawListener true
            val current = FloatingBottomBarConfigStore.read()
            when {
                current.implementation != BottomBarImplementation.NEW_VIEW -> restore()
                current.mode != installedMode -> {
                    if (!reinstallPosted) {
                        reinstallPosted = true
                        host.post {
                            reinstallPosted = false
                            reconcile(activity, activity.window.decorView, 0)
                        }
                    }
                }
                else -> {
                    val scaleChanged = applyScale(current.scale, current.position)
                    applyPosition(current.position)
                    centerTabContent(row)
                    keepOwnBarChromeHidden(parent, tabView)
                    val dark = runCatching { com.owo233.tcqt.HookEnv.isNightMode() }.getOrDefault(false)
                    if (appliedDark != dark) {
                        appliedDark = dark
                        host.setDarkTheme(dark)
                        (surfaceRef.get() as? FloatingBarSurfaceView)?.update(current.mode, dark)
                        (surfaceRef.get() as? GlassPillView)?.setTheme(dark)
                        (indicatorRef.get() as? GlassDropletView)?.setTheme(dark)
                        (indicatorRef.get() as? FloatingBarIndicatorView)?.setTheme(dark)
                    }
                    (surfaceRef.get() as? GlassPillView)?.setBlurPercent(current.blurPercent)
                    (indicatorRef.get() as? GlassDropletView)?.setBlurPercent(current.blurPercent)
                    if (indicator.visibility != View.VISIBLE) indicator.visibility = View.VISIBLE
                    val selected = QQTabLocator.selectedIndex(row).takeIf { it >= 0 }
                        ?: QQTabLocator.currentIndex(tabView)
                    if (layoutSyncPending) {
                        if (!scaleChanged) {
                            // The previous pass requested new LayoutParams;
                            // this pre-draw runs after that traversal and
                            // therefore sees the final child bounds.
                            if (selected >= 0 && host.width > 0 && row.width > 0 && tabView.width > 0) {
                                driver?.animateToIndex(selected, true)
                                layoutSyncPending = false
                            }
                        }
                    } else if (selected >= 0) {
                        driver?.animateToIndex(selected, false)
                    }
                }
            }
            true
        }
        refreshListener = listener
        host.viewTreeObserver.addOnPreDrawListener(listener)
        Log.i("NewView 悬浮底栏已安装，复用原生 Tab 内容")
        return true
    }

    private fun restoreFailed(parent: ViewGroup, tabView: ViewGroup, host: ViewGroup) {
        runCatching {
            (tabView.parent as? ViewGroup)?.removeView(tabView)
            (host.parent as? ViewGroup)?.removeView(host)
            restoreViews(tabView, parent)
        }
    }

    private fun restore() {
        val host = hostRef.get() ?: return
        val tabView = tabViewRef.get() ?: return
        val parent = parentRef.get() ?: (host.parent as? ViewGroup) ?: return
        refreshListener?.let { runCatching { host.viewTreeObserver.removeOnPreDrawListener(it) } }
        driver = null
        (tabView.parent as? ViewGroup)?.removeView(tabView)
        (host.parent as? ViewGroup)?.removeView(host)
        restoreViews(tabView, parent)

        hostRef = WeakReference(null)
        tabViewRef = WeakReference(null)
        tabRowRef = WeakReference(null)
        backdropRef = WeakReference(null)
        indicatorRef = WeakReference(null)
        surfaceRef = WeakReference(null)
        parentRef = WeakReference(null)
        refreshListener = null
        originalBackgrounds.clear()
        hiddenChrome.clear()
        appliedScale = 0f
        appliedDark = null
        installedMode = null
        installedPosition = null
        layoutSyncPending = false
        navigationInset = 0
        reinstallPosted = false
        Log.i("NewView 悬浮底栏已恢复原生父级")
    }

    private fun restoreViews(tabView: ViewGroup, parent: ViewGroup) {
        originalChildren.forEach { saved ->
            val lp = saved.view.layoutParams ?: return@forEach
            lp.width = saved.width
            lp.height = saved.height
            (lp as? LinearLayout.LayoutParams)?.weight = saved.weight
            saved.view.layoutParams = lp
        }
        restoreTabContentTranslations()
        restoreBackgrounds()
        tabView.setPadding(originalTabPadding[0], originalTabPadding[1], originalTabPadding[2], originalTabPadding[3])
        tabView.alpha = originalTabAlpha
        tabView.background = originalTabBackground
        if (tabView is TabWidget) {
            tabView.setStripEnabled(originalStripEnabled)
            tabView.dividerDrawable = originalDividerDrawable
        }
        QQTabLocator.findTabRow(tabView)?.setPadding(
            originalRowPadding[0], originalRowPadding[1], originalRowPadding[2], originalRowPadding[3],
        )
        hiddenChrome.forEach { (view, visibility) -> view.visibility = visibility }
        if (tabView.parent == null) {
            val index = originalIndex.coerceIn(0, parent.childCount)
            originalTabParams?.let { parent.addView(tabView, index, it) } ?: parent.addView(tabView, index)
        }
    }

    private fun applyScale(scale: Float, position: FloatingBottomBarPosition): Boolean {
        val host = hostRef.get() ?: return false
        val tabView = tabViewRef.get() ?: return false
        val row = tabRowRef.get() ?: return false
        val normalized = scale.coerceIn(0.8f, 1.2f)
        if (abs(appliedScale - normalized) < 0.001f) return false
        val density = host.resources.displayMetrics.density
        val geometry = FloatingBarGeometry.fromBase(
            baseBarHeight = baseBarHeight,
            baseTabWidth = baseTabWidth,
            baseTabHeight = baseBarHeight,
            baseShadowPadding = (BASE_SHADOW_DP * density).roundToInt(),
            tabCount = QQTabLocator.tabCount(row),
            scale = normalized,
        )
        host.setGeometryScale(normalized)
        val hostLp = host.layoutParams as? FrameLayout.LayoutParams ?: return false
        hostLp.width = geometry.totalWidth + geometry.shadowPadding * 2
        hostLp.height = geometry.barHeight + geometry.shadowPadding * 2
        val inset = effectiveNavigationInset(tabView)
        hostLp.bottomMargin = floatingOffset(position, inset, density) - geometry.shadowPadding + inset
        host.layoutParams = hostLp
        val tabLp = tabView.layoutParams as? FrameLayout.LayoutParams ?: return false
        tabLp.width = geometry.totalWidth
        tabLp.height = geometry.barHeight
        tabView.layoutParams = tabLp

        row.setPadding(geometry.horizontalPadding, 0, geometry.horizontalPadding, 0)
        centerTabContent(row)
        row.children.filter { !it.isGone }.forEach { child ->
            val lp = child.layoutParams ?: return@forEach
            lp.width = geometry.tabWidth
            val original = originalChildren.firstOrNull { it.view === child }
            if (original != null && original.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                // Keep the icon/title gap at its native size; the enclosing cell
                // grows with the bar and the pair is re-centered below.
                lp.height = original.height
            }
            (lp as? LinearLayout.LayoutParams)?.weight = 0f
            child.layoutParams = lp
        }
        indicatorRef.get()?.let { indicator ->
            val lp = indicator.layoutParams as? FrameLayout.LayoutParams ?: return@let
            lp.width = geometry.tabWidth
            lp.height = max(1, geometry.tabHeight - geometry.horizontalPadding * 2)
            lp.leftMargin = geometry.horizontalPadding
            lp.topMargin = geometry.horizontalPadding
            indicator.layoutParams = lp
        }
        appliedScale = normalized
        layoutSyncPending = true
        host.requestLayout()
        Log.i("NewView 底栏几何缩放: ${FloatingBottomBarConfigStore.percent(normalized)}%")
        return true
    }

    private fun applyPosition(position: FloatingBottomBarPosition) {
        val host = hostRef.get() ?: return
        val tabView = tabViewRef.get() ?: return
        val lp = host.layoutParams as? FrameLayout.LayoutParams ?: return
        val inset = effectiveNavigationInset(tabView)
        val desired = floatingOffset(position, inset, host.resources.displayMetrics.density) - host.shadowPadding + inset
        if (lp.bottomMargin == desired && installedPosition == position) return
        lp.bottomMargin = desired
        host.layoutParams = lp
        installedPosition = position
        host.requestLayout()
    }

    /** Keep the native icon/title group centered after the host's height changes. */
    private fun centerTabContent(row: ViewGroup) {
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

    private fun restoreTabContentTranslations() {
        val row = tabRowRef.get() ?: return
        row.children.forEach(::restoreTranslations)
    }

    private fun restoreTranslations(view: View) {
        (view.getTag(CONTENT_TRANSLATION_TAG) as? Float)?.let {
            view.translationY = it
            view.setTag(CONTENT_TRANSLATION_TAG, null)
        }
        if (view is ViewGroup) view.children.forEach(::restoreTranslations)
    }

    /** Re-layout QQ's independently anchored icon and title as one centered group. */
    private fun centerIconTitlePair(tab: View) {
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

    private fun relativeTop(view: View, ancestor: View): Int {
        var result = 0
        var current: View? = view
        while (current != null && current !== ancestor) {
            result += current.top
            if (current !== view) result += current.translationY.roundToInt()
            current = current.parent as? View
        }
        return result
    }

    private fun findIconTitlePair(view: View): Pair<View, View>? {
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

    private fun findIconCandidate(view: View): View? =
        if (QQTabLocator.isTabIcon(view)) view
        else if (view is android.widget.ImageView) view
        else (view as? ViewGroup)?.children?.firstNotNullOfOrNull(::findIconCandidate)

    private fun findTitleCandidate(view: View): View? =
        if (isTitleCandidate(view)) view else (view as? ViewGroup)?.children?.firstNotNullOfOrNull(::findTitleCandidate)

    private fun isTitleCandidate(view: View): Boolean =
        view is android.widget.TextView && !view.text.isNullOrBlank() && view.background == null

    private fun collectContentBounds(view: View, offset: Int, tabHeight: Int, out: IntArray) {
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

    private fun findBackdrop(parent: ViewGroup, tabView: View): ViewGroup? {
        var best: ViewGroup? = null
        var area = 0
        parent.children.forEach { child ->
            if (child === tabView || child !is ViewGroup || child.visibility != View.VISIBLE || QQTabLocator.isBlurWrapper(child)) return@forEach
            val candidate = child.width * child.height
            if (candidate > area) {
                area = candidate
                best = child
            }
        }
        return best
    }

    private fun hugContentWidth(tabRow: ViewGroup, density: Float): Int? {
        val visible = tabRow.children.filter { !it.isGone }.toList()
        if (visible.isEmpty()) return null
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val iconOnly = visible.all { findTabTitle(it) == null }
        var widest = 0
        visible.forEach { tab ->
            tab.measure(unspecified, unspecified)
            widest = max(widest, if (iconOnly) (ICON_ONLY_BASIS_DP * density).roundToInt() else max(tab.measuredWidth, leafContentWidth(tab)))
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

    private fun leafContentWidth(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        if (view is ViewGroup) return view.children.maxOfOrNull { child -> leafContentWidth(child) } ?: 0
        if (view.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT) return 0
        return view.width
    }

    private fun findTabTitle(view: View): View? {
        if (view is android.widget.TextView && !view.text.isNullOrBlank() && view.background == null) return view
        if (view is ViewGroup) view.children.forEach { findTabTitle(it)?.let { found -> return found } }
        return null
    }

    private fun contentBarHeight(tabRow: ViewGroup, fallback: Int): Int {
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

    private fun collectLeafBounds(view: View, offset: Int, out: IntArray) {
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

    private fun captureBackgrounds(view: View) {
        originalBackgrounds[view] = view.background
        if (view is ViewGroup) view.children.forEach { captureBackgrounds(it) }
    }

    private fun restoreBackgrounds() {
        originalBackgrounds.forEach { (view, background) -> view.background = background }
    }

    private fun stripSolidBackgrounds(view: View) {
        view.background = null
        if (view is ViewGroup) view.children.forEach { stripSolidBackgrounds(it) }
    }

    private fun hideOwnBarChrome(parent: ViewGroup, tabView: View) {
        val density = parent.resources.displayMetrics.density
        val maxThickness = max(2, (density * 1.5f).roundToInt())
        parent.children.forEach { child ->
            if (child === tabView || child.visibility != View.VISIBLE) return@forEach
            val blur = QQTabLocator.isBlurWrapper(child)
            val hairline = child !is ViewGroup && child.height in 1..maxThickness && child.width >= parent.width * 0.9f && child.background != null
            if ((blur || hairline) && !hiddenChrome.containsKey(child)) {
                hiddenChrome[child] = child.visibility
                child.visibility = View.GONE
            }
        }
    }

    private fun keepOwnBarChromeHidden(parent: ViewGroup, tabView: View) {
        hideOwnBarChrome(parent, tabView)
        hiddenChrome.keys.forEach { if (it.visibility != View.GONE) it.visibility = View.GONE }
    }

    private fun disableTabWidgetStrips(tabView: View) {
        if (tabView is TabWidget) {
            tabView.setStripEnabled(false)
            tabView.dividerDrawable = null
        }
    }

    private fun unclipAncestors(from: ViewGroup) {
        var current: ViewGroup? = from
        var steps = 0
        while (current != null && steps++ < 12) {
            current.clipChildren = false
            current.clipToPadding = false
            if (current.id == android.R.id.content) return
            current = current.parent as? ViewGroup
        }
    }

    private fun currentNavigationInset(anchor: View): Int = runCatching {
        anchor.rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
    }.getOrDefault(0)

    private fun effectiveNavigationInset(anchor: View): Int {
        val live = currentNavigationInset(anchor)
        if (live > 0) navigationInset = live
        return max(live, navigationInset)
    }

    private fun floatingOffset(position: FloatingBottomBarPosition, inset: Int, density: Float): Int =
        (position.offsetDp(inset != 0) * density).roundToInt()

    private data class OriginalChildLayout(val view: View, val width: Int, val height: Int, val weight: Float)
}
