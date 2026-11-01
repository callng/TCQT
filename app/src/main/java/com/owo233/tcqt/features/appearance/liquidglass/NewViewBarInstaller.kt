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

/**
 * NewView's renderer on top of the same structural replacement used by the classic implementation.
 * The QQ TabView remains the source of all labels, icons, badges and click
 * listeners; this class only changes its parent, geometry and visual layers.
 */
internal object NewViewBarInstaller {
    private const val MAX_INSTALL_ATTEMPTS = 40
    private const val RETRY_DELAY_MS = 250L
    private const val BASE_SHADOW_DP = 14f
    private const val BAR_HEIGHT_DP = 64f

    private var hostRef = WeakReference<FloatingBarHostLayout?>(null)
    private var tabViewRef = WeakReference<ViewGroup?>(null)
    private var tabRowRef = WeakReference<ViewGroup?>(null)
    private var backdropRef = WeakReference<ViewGroup?>(null)
    private var indicatorRef = WeakReference<View?>(null)
    private var surfaceRef = WeakReference<View?>(null)
    private var parentRef = WeakReference<ViewGroup?>(null)
    private var activityRef = WeakReference<Activity?>(null)
    private var themeChangeActivityRef = WeakReference<Activity?>(null)
    private var driver: DropletGestureDriver? = null
    private var refreshListener: ViewTreeObserver.OnPreDrawListener? = null

    private var tabSnapshot: TabViewSnapshot? = null
    private val hiddenChrome = java.util.WeakHashMap<View, Int>()
    private val lifecycleListeners = ViewListenerScope()

    private var baseBarHeight = 0
    private var baseTabWidth = 0
    private var appliedScale = 0f
    private var appliedWidthBudget = 0
    private var appliedDark: Boolean? = null
    private var installedMode: FloatingBottomBarMode? = null
    private var installedPosition: FloatingBottomBarPosition? = null
    /** Captured before the native TabView is detached; detached views often report zero insets. */
    private var navigationInset = 0
    private var reinstallPosted = false
    /** A geometry change needs one post-layout snap before normal animations resume. */
    private var layoutSyncPending = false

    fun currentPager(): ViewGroup? = backdropRef.get()

    fun onHostThemeChanging() {
        val activity = activityRef.get() ?: return
        restore()
        themeChangeActivityRef = WeakReference(activity)
    }

    fun onHostThemeChanged() {
        val activity = themeChangeActivityRef.get() ?: return
        themeChangeActivityRef.clear()
        // Wait for QQ's theme callback to finish before measuring the new tab content.
        scheduleInstall(activity)
    }

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
                // Log.w("NewView 底栏安装条件未满足，放弃本次安装")
            }
            return
        }
        applyScale(config.scale, config.position)
    }

    private fun install(activity: Activity, tabView: ViewGroup): Boolean {
        val parent = tabView.parent as? ViewGroup ?: return false
        if (parent is FloatingBarHostLayout || parent is GlassBarHostLayout) return false
        val row = QQTabLocator.findTabRow(tabView) ?: return false
        val backdrop = TabViewOperations.findBackdrop(parent, tabView) ?: return false
        val visibleTabs = row.children.filter { !it.isGone }.toList()
        if (visibleTabs.isEmpty()) return false

        val context = tabView.context
        val density = context.resources.displayMetrics.density
        val targetHeight = (BAR_HEIGHT_DP * density).roundToInt()
        val navigationReserve = tabView.paddingBottom
        navigationInset = max(currentNavigationInset(tabView), navigationReserve)
        val contentHeight = NewViewTabContentLayout.contentBarHeight(row, tabView.height - navigationReserve)
        val resolvedHeight = max(targetHeight, contentHeight)

        val snapshot = TabViewSnapshot(tabView)
        if (snapshot.originalIndex < 0) return false
        tabSnapshot = snapshot
        hiddenChrome.clear()

        val barWidth = NewViewTabContentLayout.hugContentWidth(row, density) ?: run {
            snapshot.restore(); tabSnapshot = null; return false
        }

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
            parent.addView(host, snapshot.originalIndex, hostParams)
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
            TabViewOperations.disableTabWidgetStrips(tabView)
            hideOwnBarChrome(parent, tabView)
        } catch (t: Throwable) {
            restoreFailed(parent, tabView, host)
            Log.e("NewView 底栏重新父级化失败", t)
            return false
        }

        TabViewOperations.unclipAncestors(parent)
        hostRef = WeakReference(host)
        lifecycleListeners.onDetached(host) { if (hostRef.get() === host) restore() }
        tabViewRef = WeakReference(tabView)
        tabRowRef = WeakReference(row)
        backdropRef = WeakReference(backdrop)
        indicatorRef = WeakReference(indicator)
        surfaceRef = WeakReference(surface)
        parentRef = WeakReference(parent)
        activityRef = WeakReference(activity)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (indicator as? GlassDropletView)?.setPill(surface)
        }

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
                    NewViewTabContentLayout.centerTabContent(row)
                    keepOwnBarChromeHidden(parent, tabView)
                    val dark =
                        runCatching { com.owo233.tcqt.core.env.HookEnv.isNightMode() }.getOrDefault(
                            false
                        )
                    if (appliedDark != dark) {
                        appliedDark = dark
                        host.setDarkTheme(dark)
                        (surfaceRef.get() as? FloatingBarSurfaceView)?.update(current.mode, dark)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            (surfaceRef.get() as? GlassPillView)?.setTheme(dark)
                            (indicatorRef.get() as? GlassDropletView)?.setTheme(dark)
                        }
                        (indicatorRef.get() as? FloatingBarIndicatorView)?.setTheme(dark)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        (surfaceRef.get() as? GlassPillView)?.setBlurPercent(current.blurPercent)
                        (indicatorRef.get() as? GlassDropletView)?.setBlurPercent(current.blurPercent)
                    }
                    if (indicator.visibility != View.VISIBLE) indicator.visibility = View.VISIBLE
                    val selected = QQTabLocator.selectedIndex(row).takeIf { it >= 0 }
                        ?: QQTabLocator.currentIndex(tabView)
                    if (layoutSyncPending) {
                        if (!scaleChanged) {
                            // The previous pass requested new LayoutParams; this pre-draw runs
                            // after that traversal and sees the final child bounds.
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
        // Log.i("NewView 悬浮底栏已安装，复用原生 Tab 内容")
        return true
    }

    private fun restoreFailed(parent: ViewGroup, tabView: ViewGroup, host: ViewGroup) {
        runCatching {
            (tabView.parent as? ViewGroup)?.removeView(tabView)
            (host.parent as? ViewGroup)?.removeView(host)
            restoreViews(tabView, parent)
        }
    }

    fun restore() {
        val host = hostRef.get()
        hostRef = WeakReference(null)
        lifecycleListeners.clear()
        refreshListener?.let { listener -> host?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener) }
        driver = null
        hiddenChrome.forEach { (view, visibility) -> view.visibility = visibility }
        tabSnapshot?.restore()
        tabSnapshot = null
        (host?.parent as? ViewGroup)?.removeView(host)
        tabViewRef.clear(); tabRowRef.clear(); backdropRef.clear()
        indicatorRef.clear(); surfaceRef.clear(); parentRef.clear(); activityRef.clear()
        refreshListener = null
        hiddenChrome.clear()
        appliedScale = 0f; appliedWidthBudget = 0; appliedDark = null; installedMode = null; installedPosition = null
        layoutSyncPending = false; navigationInset = 0; reinstallPosted = false
    }

    private fun restoreViews(tabView: ViewGroup, parent: ViewGroup) {
        hiddenChrome.forEach { (view, visibility) -> view.visibility = visibility }
        tabSnapshot?.restore()
        tabSnapshot = null
    }

    private fun applyScale(scale: Float, position: FloatingBottomBarPosition): Boolean {
        val host = hostRef.get() ?: return false
        val tabView = tabViewRef.get() ?: return false
        val row = tabRowRef.get() ?: return false
        val normalized = scale.coerceIn(0.8f, 1.2f)
        val density = host.resources.displayMetrics.density
        val windowWidth = host.rootView.width.takeIf { it > 0 } ?: host.resources.displayMetrics.widthPixels
        val widthBudget = (windowWidth - (24f * density).roundToInt()).coerceAtLeast(1)
        if (abs(appliedScale - normalized) < 0.001f && appliedWidthBudget == widthBudget) return false
        val geometry = FloatingBarGeometry.fromBase(
            baseBarHeight = baseBarHeight,
            baseTabWidth = baseTabWidth,
            baseTabHeight = baseBarHeight,
            baseShadowPadding = (BASE_SHADOW_DP * density).roundToInt(),
            tabCount = QQTabLocator.tabCount(row),
            scale = normalized,
            maximumWidth = widthBudget,
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
        NewViewTabContentLayout.centerTabContent(row)
        row.children.filter { !it.isGone }.forEach { child ->
            val lp = child.layoutParams ?: return@forEach
            lp.width = geometry.tabWidth
            val originalHeight = tabSnapshot?.originalHeight(child)
            if (originalHeight != null && originalHeight != ViewGroup.LayoutParams.MATCH_PARENT) {
                // Keep the icon/title gap at its native size; the enclosing cell
                // grows with the bar and the pair is re-centered below.
                lp.height = originalHeight
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
        appliedWidthBudget = widthBudget
        appliedScale = normalized
        layoutSyncPending = true
        host.requestLayout()
        // Log.i("NewView 底栏几何缩放: ${FloatingBottomBarConfigStore.percent(normalized)}%")
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


    private fun currentNavigationInset(anchor: View): Int = runCatching {
        androidx.core.view.ViewCompat.getRootWindowInsets(anchor)
            ?.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    }.getOrDefault(0)

    private fun effectiveNavigationInset(anchor: View): Int {
        val live = currentNavigationInset(anchor)
        if (live > 0) navigationInset = live
        return max(live, navigationInset)
    }

    private fun floatingOffset(position: FloatingBottomBarPosition, inset: Int, density: Float): Int =
        (position.offsetDp(inset != 0) * density).roundToInt()

}
