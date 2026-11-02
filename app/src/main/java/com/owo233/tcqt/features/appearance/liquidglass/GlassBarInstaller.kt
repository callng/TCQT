package com.owo233.tcqt.features.appearance.liquidglass

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.appearance.liquidglass.ClassicTabContentLayout.contentBarHeight
import com.owo233.tcqt.features.appearance.liquidglass.ClassicTabContentLayout.dropNavigationReserve
import com.owo233.tcqt.features.appearance.liquidglass.ClassicTabContentLayout.hugContentWidth
import com.owo233.tcqt.features.appearance.liquidglass.ClassicTabContentLayout.isIconOnlyRow
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 玻璃栏的安装与生命周期协调。
 *
 * 核心是「布局手术」而非重建：宿主内容区本就铺满全屏、底栏以 bottom 定位悬浮其上，
 * 只需把底栏整体搬入浮动容器并清空其自带的不透明底色，玻璃折射的背景天然成立。
 * 原生 Tab 的红点、未读数、长按菜单等能力全部保留，仅外观被重塑。
 *
 * 安装触发有两条通路：底栏切换方法的一次调用（首次选中初始 Tab 时底栏必然已存在），
 * 以及主界面恢复后的限时轮询兜底（冷启动时底栏可能数秒后才出现）。
 */

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object GlassBarInstaller {

    /** 主界面构建为异步过程，轮询兜底的次数与间隔（共约 10 秒）。 */
    private const val MAX_INSTALL_ATTEMPTS = 40
    private const val RETRY_DELAY_MS = 250L

    /** 原生底栏的隐藏窗口期上限，超时后交还，避免闪烁变永久不可见。 */
    private const val REVEAL_TIMEOUT_MS = 8000L


    /** KernelSU FloatingBottomBar geometry baseline. */
    private const val BAR_HEIGHT_DP = 64f






    /** 绑定行不可用时的容忍帧数；超过即认为旧绑定已死。 */
    private const val TAB_ROW_EMPTY_FRAME_LIMIT = 120

    /** 逐帧复用坐标缓冲；全部调用位于 UI 线程。 */
    private val tmpLoc = IntArray(2)

    /** 逐帧复用的宿主坐标系缓冲；全部调用位于 UI 线程。 */
    private val tmpPos = IntArray(2)


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

    /** 绑定行暂时不可用时已等待的帧数；超过上限即放弃旧绑定。 */
    private var tabRowEmptyFrames = 0

    /** 安装时记录的内容高度，用于皮肤刷新后的高度复位。 */
    private var barHeight = 0

    /** 液滴在宿主容器内的基础纵坐标（不含底栏平移）。 */
    private var dropletBaseY = 0f

    /** 液滴尺寸与偏移最后一次测量所依据的几何指纹。 */
    private var dropletGeometryKey = 0
    private var dropletLogged = false

    /**
     * 已被补充底部留白的滚动容器，及最后使用的留白量。
     *
     * 留在这里是为了让逐帧路径不必再走一遍视图树：宿主会在自己的布局回合里恢复自己的
     * 视图状态，而一个悄悄变回 `clipToPadding = true` 的滚动容器会把留白带画成一条
     * 不透明色块——就是滚动到一半时糊在内容上的那块白斑。
     */

    /** 页面拉伸的慢速重跑节拍；见 [maintainPageExtent]。 */

    /** 最近一次非零的导航栏内边距，跨 Activity 重建保持。 */
    private var navigationInset = 0

    private val pageLayout = GlassPageLayoutController({ hostRef.get() }, { pagerRef.get() }, { barHeight })
    private val listeners = ViewListenerScope()
    private var tabSnapshot: TabViewSnapshot? = null
    private val hiddenChrome = java.util.WeakHashMap<View, Int>()
    private val stockAlpha = java.util.WeakHashMap<View, Float>()
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
     * 在 pre-draw（内容上屏前最后一站）按类名匹配底栏并压低透明度；同时隐藏宿主自绘
     * 的毛玻璃长条——它独立于底栏存在，底栏淡出期间仍会以灰条横亘屏幕底部。
     * 超时未接管则原样交还。
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
                            current.alpha = stockAlpha.remove(current) ?: 1f
                            (current.parent as? ViewGroup)?.let { showOwnBlurLayers(it) }
                            Log.w("玻璃药丸迟迟未接管，原生底栏已交还")
                        }
                        decor.viewTreeObserver.removeOnPreDrawListener(this)
                    }

                    else -> if (bar?.alpha != 0f) {
                        bar?.let { stockAlpha.putIfAbsent(it, it.alpha); it.alpha = 0f }
                        (bar?.parent as? ViewGroup)?.let { hideOwnBlurLayers(it) }
                    }
                }
                return true
            }
        })
    }

    private fun tryInstall(activity: Activity, decor: View, attempt: Int) {
        runCatching {
            if (activity.isFinishing || activity.isDestroyed || FloatingBottomBarConfigStore.read().implementation != BottomBarImplementation.CLASSIC) return

            // 仅当药丸存活于「当前窗口」时才跳过：进程可能比被划走的界面活得久，
            // 残留引用若被视作已安装，重启的主界面将停留在原生底栏上。
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
    fun restore() = resetState()

    private fun resetState() {
        val oldHost = hostRef.get()
        hostRef = WeakReference(null)
        listeners.clear()
        hiddenChrome.forEach { (view, visibility) -> view.visibility = visibility }
        hiddenChrome.clear()
        pageLayout.reset()
        tabSnapshot?.restore()
        tabSnapshot = null
        (oldHost?.parent as? ViewGroup)?.removeView(oldHost)
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
        tabRowEmptyFrames = 0
        barHeight = 0
        lastIndex = -1
        dropletBaseY = 0f
        dropletGeometryKey = 0
        dropletLogged = false
        Log.i("检测到上个界面的残留状态，已重置并重新安装")
    }

    // ---- 安装主体 ----

    private fun install(tabView: ViewGroup) {
        val parent = tabView.parent as? ViewGroup ?: return
        if (parent is GlassBarHostLayout) return

        val backdrop = TabViewOperations.findBackdrop(parent, tabView) ?: run {
            Log.w("未找到可折射的背景兄弟容器，放弃安装")
            return
        }

        val index = parent.indexOfChild(tabView)
        if (index < 0) return
        val originalParams = tabView.layoutParams

        val context = tabView.context
        val density = context.resources.displayMetrics.density
        val initialInset = currentNavInset(tabView)
        val floatOffset = floatingOffset(FloatingBottomBarConfigStore.read().position, initialInset, density)

        // 导航栏内边距须在底栏尚处原位时读取：摘出后的视图不再上报任何内边距。
        val navigationReserve = tabView.paddingBottom
        val tabRow = QQTabLocator.findTabRow(tabView)
        val resolvedHeight = max(
            (BAR_HEIGHT_DP * density).roundToInt(),
            contentBarHeight(tabRow, tabView.height - navigationReserve),
        )

        val host = GlassBarHostLayout(context, backdrop, tabView)
        host.setupShadow(host.isDarkTheme)
        val shadowPad = host.shadowPad

        val geometry = TabViewSnapshot(tabView, stockAlpha.remove(tabView) ?: tabView.alpha)
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

        // 结构性迁移必须是原子事务：所有可能失败的准备已在上方完成，此处失败则按原索引放回。
        runCatching {
            parent.removeView(tabView)
            parent.addView(host, index, hostParams)
            host.addView(tabView, tabParams)
        }.onFailure {
            geometry.restore()
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
            runCatching { geometry.restore() }
            restoreFailedReparent(parent, tabView, host, index, originalParams)
            Log.e("浮动底栏尺寸调整失败", it)
            return
        }

        // 玻璃层在本轮内即加入其下，两者同时出现，可以安全恢复可见。
        tabView.alpha = 1f

        tabSnapshot = geometry
        hostRef = WeakReference(host)
        listeners.onDetached(host) { if (hostRef.get() === host) restore() }
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
            TabViewOperations.disableTabWidgetStrips(tabView)
        }.onFailure { Log.e("清理原生底栏装饰失败", it) }

        // 宿主自身已按边到边布局，导航栏内边距无条件计入锚点。
        val inset = rememberNavigationInset(parent)
        hostParams.bottomMargin = floatingOffset(FloatingBottomBarConfigStore.read().position, inset, density) - shadowPad + inset
        host.layoutParams = hostParams
        host.post { syncHostBottomInset(host, navigationInset) }

        TabViewOperations.unclipAncestors(parent)
        attachRenderer(host, backdrop, density)
        installSelectionWatcher(host)

        // 页面重建时重新拉伸内容，令列表持续延伸到药丸之下。
        pageLayout.observeBackdrop(backdrop)

        host.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            var done = false

            override fun onGlobalLayout() {
                if (done) return
                done = true
                host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                host.attachThemeProbe()
                syncDropletSize(QQTabLocator.currentIndex(tabView))
                pageLayout.extendPagesToBottom(backdrop)
                // 窗口尺寸变化略滞后于布局，补一次延迟拉伸。
                host.postDelayed({ pageLayout.extendPagesToBottom(backdrop) }, 500L)
                // Log.i("液态玻璃已安装: hostW=${host.width} hostH=${host.height} barH=${tabView.height}")
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

            // 液滴按住时会越出药丸边界：FrameLayout 默认裁剪到内边距，宿主又带投影内边距，
            // 必须双重关闭裁剪。
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
            listeners.preDraw(host) { glass.invalidate(); true }
        }.onFailure { Log.e("渲染层挂载失败", it) }
    }

    // ---- 选中观察 ----

    /**
     * 逐帧观察选中项与底栏状态。
     *
     * 底栏切换方法在普通 Tab 点击时并不触发、页面切换亦无通知，
     * 唯一可靠的信号是子项的 selected 状态，只能轮询读取。
     */
    private fun installSelectionWatcher(host: GlassBarHostLayout) {
        listeners.preDraw(host) {
            if (host !== hostRef.get() || !host.isAttachedToWindow) return@preDraw true
            if (FloatingBottomBarConfigStore.read().implementation != BottomBarImplementation.CLASSIC) {
                restore()
                return@preDraw true
            }
            runCatching { holdOwnBarChromeHidden(host) }.onFailure {
                if (!keepFailedReported) {
                    keepFailedReported = true
                    Log.e("底栏装饰保持隐藏失败", it)
                }
            }
            runCatching {
                if (restoreBarContentHeight(host)) return@preDraw true
                followBarOffset(host)
                if (scheduleStructureRefreshIfNeeded(host)) return@preDraw true
                // 逐帧保持诚实：宿主会在自己的布局回合里撤销一部分页面手术——滚动容器的
                // 裁剪内边距、页面的延伸高度——且都不以布局变化的形式通知出来。
                pageLayout.maintainScrollerPadding()
                pageLayout.maintainPageExtent()
                maintainDropletGeometry()
                val tabRow = tabRowRef.get()
                val selected = QQTabLocator.selectedIndex(tabRow)
                if (selected >= 0 && selected != lastIndex) {
                    val first = lastIndex < 0
                    lastIndex = selected
                    syncDropletSize(selected)
                    dragDriver?.animateToIndex(selected, first)
                    pagerRef.get()?.let { pager -> pager.post { pageLayout.extendPagesToBottom(pager) } }
                }
            }
            true
        }
    }

    /** 底栏切换方法每次调用时回调：既是普通切页的液滴驱动，也是本进程首次（或界面重建后）的安装触发点。 */
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
     * 依据 Tab 尺寸设定液滴的尺寸与纵向位置；横向位置与全部运动归手势驱动的弹簧管理。
     *
     * 一切以宿主为基准测量，而宿主的子项从它的投影内边距起排。只读一层 `top` 仅在 Tab 行
     * 位于底栏内部时成立；QQ 的 Tab 行本身就是底栏（`QQTabWidget`），那个 `top` 已经含了
     * 宿主的内边距，再加一次便把这圈内边距计了两遍，液滴整体下偏 14dp——连同横向上的同一个
     * 表达式一起探出胶囊，也就是「液滴错位」。逐级累加到宿主再减掉宿主内边距，两种形状都只计一次。
     */
    private fun syncDropletSize(index: Int) {
        runCatching {
            val droplet = dropletRef.get()
            val tabRow = tabRowRef.get()
            val host = hostRef.get()
            if (droplet == null || tabRow == null || host == null || index < 0) return
            val tab = QQTabLocator.tabAt(tabRow, index) ?: return
            if (tab.width == 0) return

            // 药丸的内框：外侧全是本模块自绘的投影内边距，液滴既不能算进去，也不能越出去。
            val pillTop = host.paddingTop
            val pillBottom = host.height - host.paddingBottom
            if (pillBottom <= pillTop || !ViewGeometry.positionIn(tab, host, tmpPos)) return

            val density = host.resources.displayMetrics.density
            val inset = (density * 4f).roundToInt()
            val w = tab.width
            // Tab 列可能比药丸还高——QQ 按旧的贴底高度给 Tab 定尺寸、由底栏裁切——
            // 因此液滴高度以药丸内高封顶，而不是跟着 Tab 探出药丸下缘。
            val h = min(tab.height, pillBottom - pillTop) - inset * 2
            if (w <= 0 || h <= 0) return

            val lp = droplet.layoutParams
            if (lp.width != w || lp.height != h) {
                lp.width = w
                lp.height = h
                droplet.layoutParams = lp
            }
            // 液滴由宿主按其内边距原点布局，故此处给出的平移量相对该原点。
            var top = tmpPos[1] + inset
            if (top + h > pillBottom) top = pillBottom - h
            if (top < pillTop) top = pillTop
            dropletBaseY = (top - pillTop).toFloat()
            droplet.translationY = dropletBaseY
            droplet.visibility = View.VISIBLE
            dropletGeometryKey = computeDropletGeometryKey(host, tab)
            if (!dropletLogged) {
                dropletLogged = true
                /*Log.i(
                    "液滴已就位: tab=${tmpPos[0]},${tmpPos[1]} size=${w}x$h " +
                            "pill=$pillTop..$pillBottom hostPad=${host.paddingLeft},${host.paddingTop} " +
                            "tabH=${tab.height}"
                )*/
            }
        }.onFailure { Log.e("液滴尺寸同步失败", it) }
    }

    /** 液滴上次定尺寸时所依据的底栏几何指纹。 */
    private fun computeDropletGeometryKey(host: GlassBarHostLayout, tab: View): Int {
        var key = tab.width
        key = key * 131 + tab.height
        key = key * 131 + tmpPos[1]
        key = key * 131 + host.height
        return key
    }

    /**
     * 底栏自己在液滴脚下挪动过时，重新摆放液滴。
     *
     * 宿主会按自己的节奏重建底栏的一部分——QQ 会把 Tab 打回停靠高度——而这些都不以选中
     * 变化的形式通知出来。没有这一步，液滴会一直沿用按旧底栏量出的尺寸与偏移直到下一次
     * 切页，那正是「玻璃挂在底栏外面」的样子。
     */
    private fun maintainDropletGeometry() {
        if (lastIndex < 0 || dropletRef.get() == null) return
        val tabRow = tabRowRef.get() ?: return
        val host = hostRef.get() ?: return
        val tab = QQTabLocator.tabAt(tabRow, lastIndex) ?: return
        if (!ViewGeometry.positionIn(tab, host, tmpPos)) return
        if (computeDropletGeometryKey(host, tab) == dropletGeometryKey) return
        syncDropletSize(lastIndex)
    }

    /**
     * 逐帧重申滚动容器的底部留白与裁剪开关。
     *
     * 宿主在自己的布局回合里会把两者恢复回原样，且不会有任何我们监听到的布局变化。
     * 常态下只花几次属性比较。
     */
    // ---- 逐帧维持 ----

    /**
     * 皮肤刷新会重建或重新点亮底栏装饰：pre-draw 每帧重扫兄弟列表，
     * 覆盖「重新显示的实例」与「被替换的新实例」。
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
        // Log.i("宿主重新点亮了自绘装饰层，已保持隐藏: ${view.javaClass.simpleName}")
    }

    /** 皮肤刷新会恢复底栏的导航预留与停靠高度；拓扑指纹刻意不含几何，该不变量单独在 pre-draw 路径上维持。 */
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
     * 行拓扑指纹：包含身份与 LayoutParams 标识，刻意排除随布局变化的几何量。
     * 宿主可在运行时增删 Tab 或替换内部行容器，指纹变化即触发重新定宽与重新绑定。
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
     * @return true 表示观察器应等待新行重新绑定
     */
    private fun scheduleStructureRefreshIfNeeded(host: GlassBarHostLayout): Boolean {
        val tabView = tabViewRef.get() as? ViewGroup ?: return false
        val current = QQTabLocator.findTabRow(tabView)
        if (current == null || current.visibility != View.VISIBLE ||
            QQTabLocator.tabCount(current) == 0
        ) {
            // 宿主应用设置的瞬间会短暂拆空行容器，保持旧绑定，下帧重试；但不能无限重试：
            // 一个再也不会回来的行会让本方法永远返回 true，连带把选中观察器与液滴一起冻住。
            val bound = tabRowRef.get()
            val boundUsable = bound != null && bound.isAttachedToWindow &&
                    bound.isVisible && QQTabLocator.tabCount(bound) > 0
            if (boundUsable) {
                tabRowEmptyFrames = 0
                return true
            }
            return ++tabRowEmptyFrames < TAB_ROW_EMPTY_FRAME_LIMIT
        }
        tabRowEmptyFrames = 0
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
            TabViewOperations.disableTabWidgetStrips(tabView)
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

    /** 沿祖先向上解除裁剪，为液滴按住时的越界放大留出通道。 */

    // ---- 背景与装饰清理 ----

    /** 玻璃折射的背景兄弟容器：底栏与页面容器同处一个 FrameLayout，取面积最大的可见兄弟。 */

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
                hiddenChrome.putIfAbsent(child, child.visibility)
                child.visibility = View.GONE
                blurLayerRef = WeakReference(child)
                // Log.i("已隐藏宿主自绘毛玻璃层: ${child.javaClass.name}")
            }
        }
    }

    private fun showOwnBlurLayers(parent: ViewGroup) {
        for (child in parent.children) {
            if (child.isGone && QQTabLocator.isBlurWrapper(child)) {
                child.visibility = hiddenChrome.remove(child) ?: View.VISIBLE
            }
        }
    }

    /**
     * 隐藏底栏顶部的发丝线。
     *
     * 它是底栏的兄弟视图（1px 全宽纯色 View），底栏内清空背景触及不到，浮动后会以一条
     * 横线残留在页面上；按形状而非类名匹配，该类名过于通用。
     */
    private fun hideBarHairline(parent: ViewGroup, tabView: View) {
        val density = parent.resources.displayMetrics.density
        val maxThickness = max(2, (density * 1.5f).roundToInt())
        for (child in parent.children) {
            if (child === tabView || child.visibility != View.VISIBLE || child is ViewGroup) continue
            if (child.height in 1..maxThickness && child.width >= parent.width * 0.9f &&
                child.background != null
            ) {
                hiddenChrome.putIfAbsent(child, child.visibility)
                child.visibility = View.GONE
                hairlineRef = WeakReference(child)
                // Log.i("已隐藏底栏 ${child.height}px 发丝线")
            }
        }
    }

    /** 关闭 TabWidget 自绘的分隔条：它们绘制于 dispatchDraw，清背景无法触及。 */

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
        val desired = floatingOffset(FloatingBottomBarConfigStore.read().position, inset, density) - host.shadowPad + max(0, inset)
        if (lp.bottomMargin == desired) return
        lp.bottomMargin = desired
        host.layoutParams = lp
        Log.i("药丸底部锚点已刷新: inset=$inset margin=$desired")
    }

    private fun floatingOffset(
        position: FloatingBottomBarPosition,
        inset: Int,
        density: Float,
    ): Int = (position.offsetDp(inset != 0) * density).roundToInt()
}
