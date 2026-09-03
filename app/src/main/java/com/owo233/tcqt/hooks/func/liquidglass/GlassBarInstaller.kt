package com.owo233.tcqt.hooks.func.liquidglass

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
import com.owo233.tcqt.utils.log.Log
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.view.isGone
import androidx.core.view.isEmpty

/**
 * 玻璃栏的安装与生命周期协调。
 *
 * 核心思路是「布局手术」而非重建：宿主的内容区本就铺满全屏、底栏以
 * bottom 定位悬浮其上，因此只需把底栏整体搬入浮动容器，清空其自带的
 * 不透明底色，玻璃折射的背景天然成立。原生 Tab 的红点、未读数、长按
 * 菜单等能力全部保留，仅外观被重塑。
 *
 * 安装触发有两条通路：底栏切换方法的一次调用（首次选中初始 Tab 时
 * 底栏必然已存在），以及主界面恢复后的限时轮询兜底（冷启动时底栏
 * 可能数秒后才出现）。
 */
internal object GlassBarInstaller {

    /** 主界面构建为异步过程，轮询兜底的次数与间隔（共约 10 秒）。 */
    private const val MAX_INSTALL_ATTEMPTS = 40
    private const val RETRY_DELAY_MS = 250L

    /** 原生底栏的隐藏窗口期上限，超时后交还，避免闪烁变永久不可见。 */
    private const val REVEAL_TIMEOUT_MS = 8000L

    /** 药丸底边距屏幕底边的距离（dp）。 */
    private const val FLOAT_OFFSET_DP = 12f

    /** 内容定宽后每个 Tab 两侧的呼吸空间（dp）。 */
    private const val TAB_BREATHING_DP = 32f

    /** 浮动药丸距屏幕左右边缘的最小留白（dp）。 */
    private const val SCREEN_MARGIN_DP = 24f

    /** 纯图标模式（标签被外置开关隐藏）下的内容基准宽度（dp）。 */
    private const val ICON_ONLY_BASIS_DP = 24f

    /** 列表末行越过药丸后的额外余量（dp）。 */
    private const val LAST_ROW_GAP_DP = 8f

    /** 逐帧复用坐标缓冲；全部调用位于 UI 线程。 */
    private val tmpLoc = IntArray(2)

    /** 可逆几何修改的视图 tag 键。 */
    private const val EXTEND_TAG = 0x7F5A0002
    private const val ICON_TRANSLATION_TAG = 0x7F5A0003

    // ---- 安装状态：以弱引用持有宿主视图，Activity 重建后自动失效并重装 ----

    private var hostRef = WeakReference<GlassBarHostLayout?>(null)
    private var tabViewRef = WeakReference<View?>(null)
    private var tabRowRef = WeakReference<ViewGroup?>(null)
    private var pagerRef = WeakReference<ViewGroup?>(null)
    private var dropletRef = WeakReference<GlassDropletView?>(null)
    private var glassRef = WeakReference<GlassPillView?>(null)
    private var blurLayerRef = WeakReference<View?>(null)
    private var hairlineRef = WeakReference<View?>(null)
    private var dragDriver: DropletGestureDriver? = null

    private var lastIndex = -1
    private var structureSignature = 0
    private var structureRefreshPosted = false

    /** 安装时记录的内容高度，用于皮肤刷新后的高度复位。 */
    private var barHeight = 0

    /** 液滴在宿主容器内的基础纵坐标（不含底栏平移）。 */
    private var dropletBaseY = 0f

    /** 最近一次非零的导航栏内边距，跨 Activity 重建保持。 */
    private var navigationInset = 0

    private var keepFailedReported = false

    /** 玻璃当前折射的页面容器。 */
    fun currentPager(): ViewGroup? = pagerRef.get()

    /** 主界面恢复后调度安装。 */
    fun scheduleInstall(activity: Activity) {
        val decor = activity.window.decorView
        if (hostRef.get() == null) hideStockBarUntilInstalled(decor)
        decor.post { tryInstall(activity, decor, 0) }
    }

    /**
     * 在原生底栏被接管前保持其不可见，消除冷启动时原底栏的闪现。
     *
     * 在 pre-draw（内容上屏前最后一站）按类名匹配底栏并压低透明度；
     * 同时隐藏宿主自绘的毛玻璃长条——它独立于底栏存在，底栏淡出期间
     * 仍会以灰条形式横亘屏幕底部。超时未接管则原样交还。
     */
    private fun hideStockBarUntilInstalled(decor: View) {
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private val deadline = SystemClock.uptimeMillis() + REVEAL_TIMEOUT_MS
            private var bar: View? = null

            override fun onPreDraw(): Boolean {
                if (bar == null || bar?.parent == null) bar = QQTabLocator.findTabView(decor)
                val installed = bar?.parent is GlassBarHostLayout
                val expired = SystemClock.uptimeMillis() > deadline
                when {
                    installed || expired -> {
                        val current = bar
                        if (current != null && !installed) {
                            current.alpha = 1f
                            (current.parent as? ViewGroup)?.let { showOwnBlurLayers(it) }
                            Log.w("玻璃药丸迟迟未接管，原生底栏已交还")
                        }
                        decor.viewTreeObserver.removeOnPreDrawListener(this)
                    }

                    else -> if (bar?.alpha != 0f) {
                        bar?.alpha = 0f
                        (bar?.parent as? ViewGroup)?.let { hideOwnBlurLayers(it) }
                    }
                }
                return true
            }
        })
    }

    private fun tryInstall(activity: Activity, decor: View, attempt: Int) {
        runCatching {
            if (activity.isFinishing || activity.isDestroyed) return

            // 仅当药丸存活于「当前窗口」时才跳过：进程可能比被划走的
            // 界面活得久，残留的引用若被视作已安装，重启的主界面
            // 将停留在原生底栏上。
            val live = hostRef.get()
            if (live != null && live.isAttachedToWindow && live.rootView === decor.rootView) return
            if (live != null) resetState()

            val tabView = QQTabLocator.locateTabView(decor)
            if (tabView == null) {
                if (attempt < MAX_INSTALL_ATTEMPTS) {
                    decor.postDelayed({ tryInstall(activity, decor, attempt + 1) }, RETRY_DELAY_MS)
                } else {
                    Log.w("轮询 $MAX_INSTALL_ATTEMPTS 次仍未找到底栏，放弃；视图树=${QQTabLocator.describeTree(decor)}")
                }
                return
            }
            install(tabView)
        }.onFailure { Log.e("玻璃栏安装失败", it) }
    }

    /** 丢弃上一个界面的残留引用，保证重建后重新安装。 */
    private fun resetState() {
        hostRef = WeakReference(null)
        tabViewRef = WeakReference(null)
        tabRowRef = WeakReference(null)
        pagerRef = WeakReference(null)
        glassRef = WeakReference(null)
        dropletRef = WeakReference(null)
        blurLayerRef = WeakReference(null)
        hairlineRef = WeakReference(null)
        dragDriver = null
        structureSignature = 0
        structureRefreshPosted = false
        barHeight = 0
        lastIndex = -1
        dropletBaseY = 0f
        Log.i("检测到上个界面的残留状态，已重置并重新安装")
    }

    // ---- 安装主体 ----

    private fun install(tabView: ViewGroup) {
        val parent = tabView.parent as? ViewGroup ?: return
        if (parent is GlassBarHostLayout) return

        val backdrop = findBackdrop(parent, tabView) ?: run {
            Log.w("未找到可折射的背景兄弟容器，放弃安装")
            return
        }

        val index = parent.indexOfChild(tabView)
        if (index < 0) return
        val originalParams = tabView.layoutParams

        val context = tabView.context
        val density = context.resources.displayMetrics.density
        val floatOffset = (density * FLOAT_OFFSET_DP).roundToInt()

        // 导航栏内边距须在底栏尚处原位时读取：底栏即将被摘出，
        // 摘出后的视图不再上报任何内边距。
        val navigationReserve = tabView.paddingBottom
        val tabRow = QQTabLocator.findTabRow(tabView)
        val resolvedHeight = contentBarHeight(tabRow, tabView.height - navigationReserve)

        val host = GlassBarHostLayout(context, backdrop, tabView)
        host.setupShadow(host.isDarkTheme)
        val shadowPad = host.shadowPad

        val geometry = TabGeometrySnapshot(tabView, tabRow)
        val hostParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = floatOffset - shadowPad
        }
        val tabParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (resolvedHeight > 0) resolvedHeight else ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.FILL_HORIZONTAL
        }

        // 结构性迁移必须是原子事务：所有可能失败的准备已在上方完成，
        // 此处失败则按原索引放回，底栏绝不悬空。
        runCatching {
            parent.removeView(tabView)
            parent.addView(host, index, hostParams)
            host.addView(tabView, tabParams)
        }.onFailure {
            restoreFailedReparent(parent, tabView, host, index, originalParams)
            Log.e("底栏重新父级化失败", it)
            return
        }

        runCatching {
            dropNavigationReserve(tabView)
            hugContentWidth(tabRow, density)?.let { barWidth ->
                hostParams.width = barWidth + shadowPad * 2
                host.layoutParams = hostParams
            }
        }.onFailure {
            runCatching { geometry.restore(density) }
            restoreFailedReparent(parent, tabView, host, index, originalParams)
            Log.e("浮动底栏尺寸调整失败", it)
            return
        }

        // 玻璃层在本轮内即加入其下，两者同时出现，可以安全恢复可见。
        tabView.alpha = 1f

        hostRef = WeakReference(host)
        tabViewRef = WeakReference(tabView)
        tabRowRef = WeakReference(tabRow)
        structureSignature = tabStructureSignature(tabRow)
        pagerRef = WeakReference(backdrop)
        barHeight = resolvedHeight
        lastIndex = -1

        QQTabLocator.tryHookPager(backdrop)

        // 外观清理失败不影响结构安装的成立。
        runCatching {
            hideOwnBlurLayers(parent)
            hideBarHairline(parent, tabView)
            stripSolidBackgrounds(tabView)
            disableTabWidgetStrips(tabView)
        }.onFailure { Log.e("清理原生底栏装饰失败", it) }

        // 宿主自身已按边到边布局，导航栏内边距无条件计入锚点。
        val inset = rememberNavigationInset(parent)
        hostParams.bottomMargin = floatOffset - shadowPad + inset
        host.layoutParams = hostParams
        host.post { syncHostBottomInset(host, navigationInset) }

        unclipAncestors(parent)
        attachRenderer(host, backdrop, density)
        installSelectionWatcher(host)

        // 页面重建时重新拉伸内容，令列表持续延伸到药丸之下。
        backdrop.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> extendPagesToBottom(backdrop) }

        host.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            var done = false

            override fun onGlobalLayout() {
                if (done) return
                done = true
                host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                host.attachThemeProbe()
                syncDropletSize(QQTabLocator.currentIndex(tabView))
                extendPagesToBottom(backdrop)
                // 窗口尺寸变化略滞后于布局，补一次延迟拉伸。
                host.postDelayed({ extendPagesToBottom(backdrop) }, 500L)
                Log.i("液态玻璃已安装: hostW=${host.width} hostH=${host.height} barH=${tabView.height}")
            }
        })
    }

    /** 迁移失败时把底栏放回原位。 */
    private fun restoreFailedReparent(
        parent: ViewGroup,
        tabView: View,
        host: GlassBarHostLayout,
        index: Int,
        originalParams: ViewGroup.LayoutParams?,
    ) {
        runCatching {
            (tabView.parent as? ViewGroup)?.takeIf { it !== parent }?.removeView(tabView)
            (host.parent as? ViewGroup)?.removeView(host)
            if (tabView.parent == null) {
                val safeIndex = index.coerceIn(0, parent.childCount)
                if (originalParams != null) parent.addView(tabView, safeIndex, originalParams)
                else parent.addView(tabView, safeIndex)
            }
            tabView.alpha = 1f
        }.onFailure { Log.e("底栏复位失败", it) }
    }

    /** 挂载玻璃渲染层与手势驱动。 */
    private fun attachRenderer(host: GlassBarHostLayout, backdrop: ViewGroup, density: Float) {
        runCatching {
            val glass = GlassPillView(host.context, backdrop, density, host.isDarkTheme)
            host.addView(
                glass, 0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            // 液滴叠在真实 Tab 之上：其折射的放大副本应成为可见内容。
            val droplet = GlassDropletView(host.context, backdrop, tabRowRef.get(), density, host.isDarkTheme)
            droplet.visibility = View.INVISIBLE
            host.addView(
                droplet,
                FrameLayout.LayoutParams(0, 0).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                },
            )
            droplet.setPill(glass)

            // 液滴按住时越出药丸边界：FrameLayout 默认裁剪到内边距，
            // 宿主又带投影内边距，必须双重关闭裁剪。
            host.clipChildren = false
            host.clipToPadding = false

            glassRef = WeakReference(glass)
            dropletRef = WeakReference(droplet)

            host.setRendererCallback(object : GlassBarHostLayout.RendererCallback {
                override fun onSize(width: Int, height: Int, cornerRadius: Float) = glass.invalidate()

                override fun onTheme(dark: Boolean) {
                    glass.setTheme(dark)
                    droplet.setTheme(dark)
                }
            })

            val tabRow = tabRowRef.get()
            if (tabRow != null) {
                dragDriver = DropletGestureDriver(droplet, tabRow, density).apply {
                    setPill(glass)
                    setHost(host)
                }.also { host.setDragHandler(it) }
            }

            // 背景逐帧重采样，玻璃随之呈现页面内容。
            host.viewTreeObserver.addOnPreDrawListener { glass.invalidate(); true }
        }.onFailure { Log.e("渲染层挂载失败", it) }
    }

    /** 迁移前记录可逆的几何状态，失败时恢复。 */
    private class TabGeometrySnapshot(tabView: View, private val row: ViewGroup?) {
        private val target = tabView
        private val tabPadding = intArrayOf(
            tabView.paddingLeft, tabView.paddingTop, tabView.paddingRight, tabView.paddingBottom,
        )
        private val rowPadding = row?.let {
            intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom)
        }
        private val tabs: List<View> = row?.children?.toList() ?: emptyList()
        private val widths = tabs.map { it.layoutParams?.width ?: 0 }
        private val weights = tabs.map { (it.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f }

        fun restore(density: Float) {
            target.setPadding(tabPadding[0], tabPadding[1], tabPadding[2], tabPadding[3])
            val currentRow = row ?: return
            alignIconOnlyRow(currentRow, false, density)
            rowPadding?.let { currentRow.setPadding(it[0], it[1], it[2], it[3]) }
            tabs.forEachIndexed { i, tab ->
                val lp = tab.layoutParams ?: return@forEachIndexed
                lp.width = widths[i]
                (lp as? LinearLayout.LayoutParams)?.weight = weights[i]
                tab.layoutParams = lp
            }
        }
    }

    // ---- 选中观察 ----

    /**
     * 逐帧观察选中项与底栏状态。
     *
     * 底栏切换方法在普通 Tab 点击时并不触发、页面切换亦无通知，
     * 唯一可靠的信号是子项的 selected 状态，只能轮询读取。
     */
    private fun installSelectionWatcher(host: GlassBarHostLayout) {
        host.viewTreeObserver.addOnPreDrawListener {
            if (host !== hostRef.get() || !host.isAttachedToWindow) return@addOnPreDrawListener true
            runCatching { holdOwnBarChromeHidden(host) }.onFailure {
                if (!keepFailedReported) {
                    keepFailedReported = true
                    Log.e("底栏装饰保持隐藏失败", it)
                }
            }
            runCatching {
                if (restoreBarContentHeight(host)) return@addOnPreDrawListener true
                followBarOffset(host)
                if (scheduleStructureRefreshIfNeeded(host)) return@addOnPreDrawListener true
                val tabRow = tabRowRef.get()
                val selected = QQTabLocator.selectedIndex(tabRow)
                if (selected >= 0 && selected != lastIndex) {
                    val first = lastIndex < 0
                    lastIndex = selected
                    syncDropletSize(selected)
                    dragDriver?.animateToIndex(selected, first)
                    pagerRef.get()?.let { pager -> pager.post { extendPagesToBottom(pager) } }
                }
            }
            true
        }
    }

    /**
     * 底栏切换方法每次调用时回调：既是普通切页的液滴驱动，
     * 也是本进程首次（或界面重建后）的安装触发点。
     */
    fun onTabChanged(tabView: View, index: Int) {
        var host = hostRef.get()
        if (host != null && !host.isAttachedToWindow) {
            resetState()
            host = null
        }
        if (host?.parent == null || tabView.parent !== host) {
            if (tabView is ViewGroup && tabView.parent != null) {
                tabView.post {
                    runCatching {
                        install(tabView)
                        syncDropletSize(resolveTabSlot(index))
                    }.onFailure { Log.e("由切页钩子触发安装失败", it) }
                }
            }
            return
        }
        val installedHost = host
        installedHost.post {
            if (scheduleStructureRefreshIfNeeded(installedHost)) return@post
            val slot = resolveTabSlot(index)
            if (slot < 0) return@post
            syncDropletSize(slot)
            dragDriver?.animateToIndex(slot, false)
        }
    }

    /** 把钩子侧索引解析为当前可见布局槽位。 */
    private fun resolveTabSlot(appIndex: Int): Int {
        val tabRow = tabRowRef.get()
        val selected = QQTabLocator.selectedIndex(tabRow)
        return if (selected >= 0) selected else QQTabLocator.slotForIndex(tabRow, appIndex)
    }

    /**
     * 依据 Tab 尺寸设定液滴的尺寸与纵向位置；横向位置与全部运动
     * 归手势驱动的弹簧管理。
     */
    private fun syncDropletSize(index: Int) {
        runCatching {
            val droplet = dropletRef.get()
            val tabRow = tabRowRef.get()
            val host = hostRef.get()
            if (droplet == null || tabRow == null || host == null || index < 0) return
            val tab = QQTabLocator.tabAt(tabRow, index) ?: return
            if (tab.width == 0) return

            val density = host.resources.displayMetrics.density
            val inset = (density * 4f).roundToInt()
            val w = tab.width
            val h = tab.height - inset * 2
            if (w <= 0 || h <= 0) return

            val lp = droplet.layoutParams
            if (lp.width != w || lp.height != h) {
                lp.width = w
                lp.height = h
                droplet.layoutParams = lp
            }
            dropletBaseY = (tab.top + tabRow.top + inset).toFloat()
            droplet.translationY = dropletBaseY
            droplet.visibility = View.VISIBLE
        }.onFailure { Log.e("液滴尺寸同步失败", it) }
    }

    // ---- 逐帧维持 ----

    /**
     * 皮肤刷新会重建或重新点亮底栏装饰，pre-draw 每帧重扫兄弟列表，
     * 覆盖「重新显示的实例」与「被替换的新实例」两种情况。
     */
    private fun holdOwnBarChromeHidden(host: GlassBarHostLayout) {
        val parent = host.parent as? ViewGroup
        val tabView = tabViewRef.get()
        if (parent != null) {
            for (child in parent.children) {
                if (child !== tabView && QQTabLocator.isBlurWrapper(child)) {
                    blurLayerRef = WeakReference(child)
                    holdHidden(child)
                }
            }
        } else {
            blurLayerRef.get()?.let { holdHidden(it) }
        }
        hairlineRef.get()?.let { holdHidden(it) }
    }

    private fun holdHidden(view: View) {
        if (view.isGone) return
        view.visibility = View.GONE
        Log.i("宿主重新点亮了自绘装饰层，已保持隐藏: ${view.javaClass.simpleName}")
    }

    /**
     * 皮肤刷新会恢复底栏的导航预留与停靠高度；拓扑指纹刻意不含几何，
     * 该不变量单独在 pre-draw 路径上维持。
     */
    private fun restoreBarContentHeight(host: GlassBarHostLayout): Boolean {
        val tabView = tabViewRef.get() ?: return false
        var changed = dropNavigationReserve(tabView) > 0
        val lp = tabView.layoutParams
        if (lp != null && barHeight > 0 && lp.height != barHeight) {
            lp.height = barHeight
            tabView.layoutParams = lp
            changed = true
        }
        if (changed) {
            tabView.requestLayout()
            host.requestLayout()
        }
        return changed
    }

    /** 玻璃层、投影与液滴随宿主底栏的平移与淡出联动。 */
    private fun followBarOffset(host: GlassBarHostLayout) {
        val tabView = tabViewRef.get() ?: return
        // 皮肤切换会把新的不透明底色交给底栏，需要持续清空。
        if (tabView.background != null) stripSolidBackgrounds(tabView)

        val translationY = tabView.translationY
        val alpha = tabView.alpha
        val gone = tabView.visibility != View.VISIBLE

        host.translationY = if (translationY == 0f) 0f else hideShortfall(host, tabView) * translationY

        val glass = glassRef.get()
        if (glass != null && glass.translationY != translationY) glass.translationY = translationY
        val droplet = dropletRef.get()
        if (droplet != null) {
            droplet.translationY = dropletBaseY + translationY
            droplet.alpha = if (gone) 0f else alpha
        }
        glass?.alpha = if (gone) 0f else alpha
        host.setShadowOffsetY(if (gone) Float.MAX_VALUE else translationY, alpha)
    }

    /** 底栏平移后仍无法完全离开屏幕时所需的额外行程比例。 */
    private fun hideShortfall(host: GlassBarHostLayout, tabView: View): Float {
        val travel = tabView.height.toFloat()
        if (travel <= 0f) return 0f
        host.getLocationOnScreen(tmpLoc)
        val pillTop = tmpLoc[1] - host.translationY + host.paddingTop
        val root = host.rootView ?: return 0f
        root.getLocationOnScreen(tmpLoc)
        val need = tmpLoc[1] + root.height - pillTop
        return max(0f, need / travel - 1f)
    }

    // ---- 结构刷新 ----

    /**
     * 行拓扑指纹：包含身份与 LayoutParams 标识，刻意排除随布局变化的
     * 几何量；宿主可在运行时增删 Tab 或替换内部行容器，指纹变化即触发
     * 重新定宽与重新绑定。
     */
    private fun tabStructureSignature(tabRow: ViewGroup?): Int {
        if (tabRow == null) return 0
        var signature = System.identityHashCode(tabRow)
        signature = signature * 31 + tabRow.visibility
        signature = signature * 31 + tabRow.childCount
        signature = signature * 31 + if (isIconOnlyRow(tabRow)) 1 else 0
        for (tab in tabRow.children) {
            signature = signature * 31 + System.identityHashCode(tab)
            signature = signature * 31 + tab.visibility
            val lp = tab.layoutParams
            signature = signature * 31 + System.identityHashCode(lp)
            val equalWeight = (lp != null && lp.width == 0) ||
                (lp as? LinearLayout.LayoutParams)?.weight != 0f
            signature = signature * 31 + if (equalWeight) 1 else 0
            if (tab is ViewGroup) {
                signature = signature * 31 + tab.childCount
                for (child in tab.children) {
                    signature = signature * 31 + System.identityHashCode(child)
                    signature = signature * 31 + child.visibility
                }
            }
        }
        return signature
    }

    /**
     * 结构需要刷新时投递修复（LayoutParams 变更须干净的布局回合）。
     *
     * @return true 表示观察器应等待新行重新绑定
     */
    private fun scheduleStructureRefreshIfNeeded(host: GlassBarHostLayout): Boolean {
        val tabView = tabViewRef.get() as? ViewGroup ?: return false
        val current = QQTabLocator.findTabRow(tabView)
        if (current == null || current.visibility != View.VISIBLE ||
            QQTabLocator.tabCount(current) == 0
        ) {
            // 宿主应用设置的瞬间会短暂拆空行容器，保持旧绑定，下帧重试。
            return tabRowRef.get() != null
        }
        if (structureRefreshPosted) return true
        if (current === tabRowRef.get() && tabStructureSignature(current) == structureSignature) {
            return false
        }
        structureRefreshPosted = true
        host.post { refreshTabStructure(host) }
        return true
    }

    /** 行拓扑变化后重新定宽并重新绑定渲染层与手势。 */
    private fun refreshTabStructure(host: GlassBarHostLayout) {
        try {
            if (host !== hostRef.get() || host.parent == null) return
            val tabView = tabViewRef.get() as? ViewGroup ?: return
            val tabRow = QQTabLocator.findTabRow(tabView)
            if (tabRow == null || tabRow.visibility != View.VISIBLE ||
                QQTabLocator.tabCount(tabRow) == 0
            ) return

            // 新建的 Tab 会带回宿主的底色、等分宽度与停靠高度，
            // 复用初始安装的同套处理。
            stripSolidBackgrounds(tabView)
            disableTabWidgetStrips(tabView)
            dropNavigationReserve(tabView)
            val tabLp = tabView.layoutParams
            if (tabLp != null && barHeight > 0 && tabLp.height != barHeight) {
                tabLp.height = barHeight
                tabView.layoutParams = tabLp
            }
            val density = host.resources.displayMetrics.density
            val barWidth = hugContentWidth(tabRow, density) ?: return
            val lp = host.layoutParams
            val desired = barWidth + host.shadowPad * 2
            if (lp != null && lp.width != desired) {
                lp.width = desired
                host.layoutParams = lp
            }

            tabRowRef = WeakReference(tabRow)
            structureSignature = tabStructureSignature(tabRow)
            dropletRef.get()?.setTabRow(tabRow)
            dragDriver?.setTabRow(tabRow)

            // 让下一个 pre-draw 以新几何吸附液滴。
            lastIndex = -1
            tabRow.requestLayout()
            tabView.requestLayout()
            host.requestLayout()
            Log.i("Tab 结构已重新绑定: children=${tabRow.childCount}")
        } catch (t: Throwable) {
            Log.e("Tab 结构刷新失败", t)
        } finally {
            structureRefreshPosted = false
        }
    }

    // ---- 尺寸与几何 ----

    /**
     * 把宿主的等分 Tab 列替换为按内容定宽的列。
     *
     * 等分宽度只在底栏横贯屏幕时有意义；浮动药丸需以内容为准。
     * 无界的 UNSPECIFIED 测量可能返回大于已布局槽位的无意义值
     * （MATCH_PARENT 子项在无界规格下行为未定义），此时弃用该值，
     * 改以叶子视图的实际宽度为准；两者取宽者作为列内容宽。
     */
    private fun hugContentWidth(tabRow: ViewGroup?, density: Float): Int? {
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
            leaf = max(leaf, leafContentWidth(tab))
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
        var tabWidth = widest + (density * TAB_BREATHING_DP).roundToInt()
        val screen = tabRow.resources.displayMetrics.widthPixels
        val maxTotal = screen - (density * SCREEN_MARGIN_DP).roundToInt()
        if (tabWidth * count + horizontalPad * 2 > maxTotal) {
            tabWidth = (maxTotal - horizontalPad * 2) / count
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
     *
     * MATCH_PARENT 的叶子只与其所在列同宽，说明不了内容宽度
     * （图标填满整列而图形只有其几分之一），直接不计入。
     */
    private fun leafContentWidth(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        if (view is ViewGroup) {
            var widest = 0
            for (child in view.children) widest = max(widest, leafContentWidth(child))
            return widest
        }
        if (view.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT) return 0
        return view.width
    }

    /**
     * 内容对称的底栏高度。
     *
     * 底栏自身高度在边到边布局下不可信（导航预留可能以不同途径混入），
     * 以图标到列顶的间距为基准上下对称推算，且只缩小不放大。
     */
    private fun contentBarHeight(tabRow: ViewGroup?, fallback: Int): Int {
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
    private fun collectLeafBounds(view: View, offset: Int, out: IntArray) {
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
    private fun dropNavigationReserve(tabView: View): Int {
        val reserve = tabView.paddingBottom
        if (reserve <= 0) return 0
        tabView.setPadding(tabView.paddingLeft, tabView.paddingTop, tabView.paddingRight, 0)
        return reserve
    }

    // ---- 纯图标布局 ----

    /** 查找 Tab 的真实标题文字，排除未读角标。 */
    private fun findTabTitle(view: View): TextView? {
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
    private fun hasUsableTabTitle(tab: View): Boolean {
        val title = findTabTitle(tab) ?: return false
        if (title.visibility != View.VISIBLE) return false
        val lp = title.layoutParams ?: return true
        return lp.width != 0 && lp.height != 0
    }

    /** 图标俱在而所有 Tab 均无可用标题时，视为纯图标布局。 */
    private fun isIconOnlyRow(tabRow: ViewGroup?): Boolean {
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

    private fun findTabIcon(view: View): View? {
        if (QQTabLocator.isTabIcon(view)) return view
        if (view is ViewGroup) {
            for (child in view.children) findTabIcon(child)?.let { return it }
        }
        return null
    }

    /** 记录/恢复视图的原始纵偏移，实现可逆的居中补偿。 */
    private fun setIconOnlyTranslation(view: View, iconOnly: Boolean, offset: Float) {
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
    private fun alignIconOnlyContent(view: View, iconOnly: Boolean, offset: Float) {
        val name = view.javaClass.name
        if (QQTabLocator.isTabIcon(view) || name.contains("Badge")) {
            setIconOnlyTranslation(view, iconOnly, offset)
        }
        if (view is ViewGroup) {
            for (child in view.children) alignIconOnlyContent(child, iconOnly, offset)
        }
    }

    private fun alignIconOnlyRow(tabRow: ViewGroup, iconOnly: Boolean, density: Float) {
        // 29dp 图标在 5dp 顶边距的 54dp Tab 内，图标中心到 Tab 中心的
        // 精确纵向偏移为 7.5dp。
        val offset = density * 7.5f
        for (tab in tabRow.children) {
            if (tab.visibility != View.GONE) alignIconOnlyContent(tab, iconOnly, offset)
        }
    }

    // ---- 页面延伸 ----

    /**
     * 把每个页面拉伸至屏幕底部并给滚动容器补充底部留白，
     * 使列表末行能滚过药丸下方、内容透过玻璃持续可见。
     */
    private fun extendPagesToBottom(pager: ViewGroup?) {
        if (pager == null) return
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

    private fun extendOnePage(page: ViewGroup, targetHeight: Int) {
        val pageHeight = max(page.height, targetHeight)
        if (pageHeight <= 0) return
        // 以页面自身 padding 形式预留的底栏高度会封顶内容，
        // 丢弃并交给滚动容器的留白接管。
        if (page.paddingBottom > 0) {
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
        val host = hostRef.get() ?: return 0
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
        view.setTag(EXTEND_TAG, true)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val parent = v.parent as? ViewGroup ?: return@addOnLayoutChangeListener
            dropParentBottomReserve(parent, v)
            val grand = parent.parent as? ViewGroup
            if (grand != null) stretchToBottom(parent, grand.height)
            stretchToBottom(v, max(parent.height, grand?.height ?: 0))
        }
    }

    /**
     * 丢弃以滚动容器父级 padding 形式存在的底栏预留。
     *
     * 动态页的整高容器带大额底部 padding，MATCH_PARENT 的信息流
     * 因此停在页内高处；信息流自身另有滚动留白，该外层预留
     * 须与页面级预留一同丢弃。
     */
    private fun dropParentBottomReserve(parent: ViewGroup, child: View) {
        val gap = parent.height - child.bottom
        val reserve = parent.paddingBottom
        val pager = pagerRef.get()
        val pageHeight = pager?.height ?: parent.height
        if (child.top > 8 || gap <= 8 || reserve <= 0 ||
            abs(parent.height - pageHeight) > 8 ||
            (barHeight > 0 && abs(gap - barHeight) > 8) ||
            abs(reserve - gap) > 8
        ) return
        parent.clipToPadding = false
        parent.setPadding(
            parent.paddingLeft, parent.paddingTop, parent.paddingRight,
            max(0, reserve - gap),
        )
    }

    /**
     * 为子树内所有滚动容器补充底部留白并关闭 padding 裁剪，
     * 使内容行透过留白带（即药丸后方与下方）持续渲染。
     */
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

    /** 沿祖先向上解除裁剪，为液滴按住时的越界放大留出通道。 */
    private fun unclipAncestors(from: ViewGroup) {
        var current: ViewGroup? = from
        var steps = 0
        while (current != null && steps < 12) {
            current.clipChildren = false
            current.clipToPadding = false
            if (current.id == android.R.id.content) return
            current = current.parent as? ViewGroup
            steps++
        }
    }

    // ---- 背景与装饰清理 ----

    /**
     * 玻璃折射的背景兄弟容器：底栏与页面容器同处一个 FrameLayout，
     * 取面积最大的可见兄弟。
     */
    private fun findBackdrop(parent: ViewGroup, tabView: View): ViewGroup? {
        var best: ViewGroup? = null
        var bestArea = 0
        for (child in parent.children) {
            if (child === tabView || child !is ViewGroup || child.visibility != View.VISIBLE ||
                QQTabLocator.isBlurWrapper(child)
            ) continue
            val area = child.width * child.height
            if (area > bestArea) {
                bestArea = area
                best = child
            }
        }
        return best
    }

    /** 清空底栏自身的底色；底栏与其行、各 Tab 列的背景一并清除。 */
    private fun stripSolidBackgrounds(view: View) {
        view.background = null
        if (view is ViewGroup) {
            for (child in view.children) {
                child.background = null
                if (child is ViewGroup) {
                    for (tab in child.children) tab.background = null
                }
            }
        }
    }

    /** 隐藏宿主自绘的毛玻璃长条。 */
    private fun hideOwnBlurLayers(parent: ViewGroup) {
        for (child in parent.children) {
            if (QQTabLocator.isBlurWrapper(child) && child.visibility != View.GONE) {
                child.visibility = View.GONE
                blurLayerRef = WeakReference(child)
                Log.i("已隐藏宿主自绘毛玻璃层: ${child.javaClass.name}")
            }
        }
    }

    private fun showOwnBlurLayers(parent: ViewGroup) {
        for (child in parent.children) {
            if (child.isGone && QQTabLocator.isBlurWrapper(child)) {
                child.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 隐藏底栏顶部的发丝线。
     *
     * 它是底栏的兄弟视图（1px 全宽纯色 View），底栏内清空背景触及不到，
     * 底栏浮动后会以一条横线残留在页面上；按形状而非类名匹配——
     * 该类名过于通用。
     */
    private fun hideBarHairline(parent: ViewGroup, tabView: View) {
        val density = parent.resources.displayMetrics.density
        val maxThickness = max(2, (density * 1.5f).roundToInt())
        for (child in parent.children) {
            if (child === tabView || child.visibility != View.VISIBLE || child is ViewGroup) continue
            if (child.height in 1..maxThickness && child.width >= parent.width * 0.9f &&
                child.background != null
            ) {
                child.visibility = View.GONE
                hairlineRef = WeakReference(child)
                Log.i("已隐藏底栏 ${child.height}px 发丝线")
            }
        }
    }

    /** 关闭 TabWidget 自绘的分隔条：它们绘制于 dispatchDraw，清背景无法触及。 */
    private fun disableTabWidgetStrips(tabView: View) {
        if (tabView is TabWidget) {
            tabView.setStripEnabled(false)
            tabView.dividerDrawable = null
        }
    }

    // ---- 导航栏锚点 ----

    /** 读取根窗口的导航栏底部内边距。 */
    private fun currentNavInset(anchor: View): Int = runCatching {
        anchor.rootWindowInsets?.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.navigationBars()
        )?.bottom ?: 0
    }.getOrDefault(0)

    /** 缓存最近一次非零的导航栏内边距，读取返回缓存后的有效值。 */
    private fun rememberNavigationInset(anchor: View): Int {
        val inset = currentNavInset(anchor)
        if (inset > 0) navigationInset = inset
        return if (inset > 0) inset else navigationInset
    }

    /** 导航栏内边距变化后刷新药丸的底部锚点。 */
    private fun syncHostBottomInset(host: GlassBarHostLayout, inset: Int) {
        val lp = host.layoutParams as? FrameLayout.LayoutParams ?: return
        val density = host.resources.displayMetrics.density
        val desired = (density * FLOAT_OFFSET_DP).roundToInt() - host.shadowPad + max(0, inset)
        if (lp.bottomMargin == desired) return
        lp.bottomMargin = desired
        host.layoutParams = lp
        Log.i("药丸底部锚点已刷新: inset=$inset margin=$desired")
    }
}
