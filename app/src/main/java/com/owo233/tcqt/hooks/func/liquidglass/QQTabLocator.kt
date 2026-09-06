package com.owo233.tcqt.hooks.func.liquidglass

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.log.Log
import java.lang.reflect.Method
import java.util.Collections
import kotlin.math.abs

/**
 * QQ 原生底部导航栏的定位与解析。
 *
 * 宿主的资源 ID 经过混淆，无法按名称查找，唯一稳定的锚点是 UI 类名。
 * QQ 同一安装包内并存新旧两套底栏（`QQTabWidget` 与灰度中的 `QQTabLayout`），
 * 由服务端开关决定实际生效者，因此定位逻辑需同时兼容两者、按实际存在者运行。
 */
internal object QQTabLocator {

    /** 主界面 Activity，底栏仅存在于该界面。 */
    const val LAUNCHER_ACTIVITY = "com.tencent.mobileqq.activity.SplashActivity"

    /** 底栏视图类名，按常见程度排序。 */
    private val TAB_VIEW_CLASSES = listOf(
        "com.tencent.mobileqq.widget.QQTabWidget",
        "com.tencent.mobileqq.widget.QQTabLayout",
    )

    /** 需要隐藏的底栏兄弟视图：宿主自绘的毛玻璃长条，夹在玻璃与页面之间会造成二次模糊。 */
    private const val BLUR_WRAPPER_CLASS = "com.tencent.qui.quiblurview.QQBlurViewWrapper"

    /** 底栏图标视图的类名后缀；混淆后包名不保真、后缀稳定。 */
    private const val ICON_CLASS_SUFFIX = "TabDragAnimationView"

    /** 底栏切换方法，宿主每次页切换都会调用，同时兼作安装触发信号。 */
    const val SWITCH_METHOD = "setCurrentTab"

    /** 配置项键名，用于读取相关配置项。 */
    const val LIQUID_GLASS_CONFIG_KEY = "liquid_glass_tab_bar.config"

    /** 平滑切页开关。 */
    const val SMOOTH_PAGE_SWITCH = 0

    /** 结构兜底识别时对 Tab 数量的合理区间。 */
    private const val MIN_TABS = 3
    private const val MAX_TABS = 5
    private const val MIN_TAB_HEIGHT_DP = 32f

    /** 底栏视图类名列表，供入口逐一尝试挂钩。 */
    val tabViewClasses: List<String> get() = TAB_VIEW_CLASSES

    /** 每种 pager 实现只挂钩一次；QQ 可能同时存在多个实现类。 */
    private val pagerHookedClasses = Collections.synchronizedSet(mutableSetOf<Class<*>>())

    /**
     * 底栏切换授权只在短窗口内有效，并绑定到发起切换时的 pager 和目标页。
     * QQ 某些版本会把 setCurrentItem 投递到下一帧，不能用 ThreadLocal 限制
     * 授权的生命周期；短窗口和一次性消费可避免误伤初始化、恢复等内部调用。
     */
    private data class SmoothArm(
        val pager: ViewGroup,
        val target: Int,
        val expiresAt: Long,
    )

    @Volatile
    private var armedSmoothTarget: SmoothArm? = null

    private const val SMOOTH_ARM_TIMEOUT_MS = 300L

    /** 按类名精确匹配底栏视图；宿主带热补丁机制，按身份判断会静默失效，名称则始终成立。 */
    fun isTabView(view: View?): Boolean =
        view != null && view.javaClass.name in TAB_VIEW_CLASSES

    /** 按类名判断是否为需要隐藏的毛玻璃长条。 */
    fun isBlurWrapper(view: View?): Boolean =
        view != null && view.javaClass.name == BLUR_WRAPPER_CLASS

    /** 按类名后缀判断是否为底栏图标视图。 */
    fun isTabIcon(view: View?): Boolean =
        view != null && view.javaClass.name.endsWith(ICON_CLASS_SUFFIX)

    /** 列出宿主视图树中与底栏相关的类名，用于安装失败时的诊断日志。 */
    fun describeTree(root: View?): String {
        val names = StringBuilder()
        collectHostViews(root, names, 0)
        return if (names.isEmpty()) "(无宿主视图)" else names.toString()
    }

    private fun collectHostViews(view: View?, out: StringBuilder, depth: Int) {
        if (view == null || depth > 30 || out.length > 2000) return
        val name = view.javaClass.name
        if (name.startsWith("com.tencent.mobileqq.") ||
            name.contains("TabView") || name.contains("TabWidget")
        ) {
            out.append(depth).append(':').append(name).append(' ')
        }
        if (view is ViewGroup) {
            for (child in view.children) collectHostViews(child, out, depth + 1)
        }
    }

    /**
     * 在视图树中定位底栏：优先按类名精确匹配，失败后按结构特征兜底。
     *
     * 结构兜底刻意从严——找不到底栏仅损失功能；错误命中则会把无关控件
     * 重新父级化为浮动药丸，直接破坏宿主界面。
     */
    fun locateTabView(root: View?): ViewGroup? =
        findTabView(root) ?: findTabRowByShape(root)?.let { shapeMatched ->
            tightestWrapper(shapeMatched).also {
                Log.w("底栏类名未命中，按结构匹配成功: ${it.javaClass.name}")
            }
        }

    /** 深度优先搜索类名匹配的底栏视图。 */
    fun findTabView(root: View?): ViewGroup? {
        when {
            root == null -> return null
            isTabView(root) -> return root as? ViewGroup
            root !is ViewGroup -> return null
        }
        for (child in root.children) {
            findTabView(child)?.let { return it }
        }
        return null
    }

    /**
     * 判断一个视图是否被布局成底部 Tab 行的模样。
     *
     * 全部几何条件必须同时满足，最终由两条行为特征裁决：子项以自身
     * 索引作为 tag，或恰有一项处于选中态——普通按钮行两者皆无。
     */
    private fun looksLikeTabRow(view: View): Boolean {
        if (view !is ViewGroup || view.visibility != View.VISIBLE ||
            view.width <= 0 || view.height <= 0
        ) return false

        var first: View? = null
        var prevRight = Int.MIN_VALUE
        var tabs = 0
        var selected = 0
        var indexTagged = true
        for (child in view.children) {
            if (child.visibility != View.VISIBLE) continue
            val firstTab = first
            if (firstTab == null) {
                first = child
            } else if (abs(child.width - firstTab.width) > 2) {
                return false // 各 Tab 共享同一宽度
            }
            if (child.left < prevRight) return false // 水平有序、互不重叠
            prevRight = child.right
            if (child.tag !is Int || child.tag != tabs) indexTagged = false
            if (child.isSelected) selected++
            tabs++
        }
        val firstTab = first ?: return false
        if (tabs !in MIN_TABS..MAX_TABS) return false

        val root = view.rootView ?: return false
        if (root.width <= 0 || root.height <= 0) return false
        if (view.width < root.width * 0.6f) return false // Tab 行横贯大半屏幕
        val density = view.resources.displayMetrics.density
        if (firstTab.height < MIN_TAB_HEIGHT_DP * density) return false

        val loc = IntArray(2)
        val rootLoc = IntArray(2)
        view.getLocationOnScreen(loc)
        root.getLocationOnScreen(rootLoc)
        val fromBottom = rootLoc[1] + root.height - (loc[1] + view.height)
        if (fromBottom > root.height * 0.25f) return false // 且位于屏幕底部

        return indexTagged || selected == 1
    }

    /** 屏幕上位置最低的、满足 Tab 行结构特征的容器。 */
    private fun findTabRowByShape(root: View?): ViewGroup? {
        if (root == null || root.visibility != View.VISIBLE) return null
        if (root is GlassBarHostLayout) return null // 已被本模块接管
        var best: ViewGroup? = if (looksLikeTabRow(root)) root as? ViewGroup else null
        if (root is ViewGroup) {
            for (child in root.children) {
                val found = findTabRowByShape(child) ?: continue
                if (best == null || lowerOnScreen(found, best)) best = found
            }
        }
        return best
    }

    private fun lowerOnScreen(a: View, b: View): Boolean {
        val la = IntArray(2)
        val lb = IntArray(2)
        a.getLocationOnScreen(la)
        b.getLocationOnScreen(lb)
        return la[1] + a.height > lb[1] + b.height
    }

    /**
     * 包裹 Tab 行的最小容器，即将被重新父级化的对象。
     *
     * 一旦某个祖先明显高于 Tab 行本身，它便是页面而非底栏，随即停止上溯。
     */
    private fun tightestWrapper(row: ViewGroup): ViewGroup {
        var best: ViewGroup = row
        var parent = row.parent
        while (parent is ViewGroup && parent !is GlassBarHostLayout) {
            if (parent.height > row.height * 1.6f) break
            best = parent
            parent = parent.parent
        }
        return best
    }

    /**
     * 解析承载各个 Tab 的横向行容器。
     *
     * Material 风格底栏把 Tab 放在横向 `LinearLayout` 子容器中；
     * `TabWidget` 系底栏则自身即为行容器，直接持有各 Tab。
     */
    fun findTabRow(tabView: ViewGroup?): ViewGroup? {
        if (tabView == null) return null
        var hiddenFallback: ViewGroup? = null
        for (child in tabView.children) {
            if (child is LinearLayout && child.orientation == LinearLayout.HORIZONTAL &&
                child.childCount >= 2
            ) {
                if (child.isVisible) return child
                if (hiddenFallback == null) hiddenFallback = child
            }
        }
        if (tabView is LinearLayout && tabView.orientation == LinearLayout.HORIZONTAL &&
            tabView.childCount >= 2
        ) return tabView
        if (looksLikeTabRow(tabView)) return tabView
        return hiddenFallback
    }

    /** 实际参与行布局的 Tab 数量（GONE 的占位项不计入）。 */
    fun tabCount(tabRow: ViewGroup?): Int {
        if (tabRow == null) return 0
        return tabRow.children.count { it.visibility != View.GONE }
    }

    /** 占据第 [slot] 个可见布局槽位的 Tab；GONE 占位项跳过。 */
    fun tabAt(tabRow: ViewGroup?, slot: Int): View? {
        if (tabRow == null || slot < 0) return null
        var current = 0
        for (child in tabRow.children) {
            if (child.isGone) continue
            if (current == slot) return child
            current++
        }
        return null
    }

    /**
     * 将宿主侧的原始子位置索引转换为行内可见布局槽位。
     *
     * QQ 未给 Tab 标记逻辑索引，直接使用原始子位置；GONE 的功能占位项
     * 在任何情况下都需要被跳过。
     */
    fun slotForIndex(tabRow: ViewGroup?, index: Int): Int {
        if (tabRow == null || index < 0) return -1
        var slot = 0
        var rawSlot = -1
        for (i in 0 until tabRow.childCount) {
            val child = tabRow.getChildAt(i)
            if (child.isGone) continue
            if (i == index) rawSlot = slot
            slot++
        }
        return rawSlot
    }

    /**
     * 从视图状态直接读取当前选中的 Tab 槽位。
     *
     * Tab 根视图在每次切换时都会被设置 `selected` 状态，可靠且零成本，
     * 是逐帧观察选中变化的首选信号。
     */
    fun selectedIndex(tabRow: ViewGroup?): Int {
        if (tabRow == null) return -1
        var slot = 0
        for (child in tabRow.children) {
            if (child.isGone) continue
            if (child.isSelected) return slot
            slot++
        }
        return -1
    }

    /** 为当前底栏切换授权一次目标页平滑切换，并确保所有 Tab 页预先保活。 */
    fun armSmoothTarget(index: Int) {
        armedSmoothTarget = null
        if (!TCQTSetting.getInt(LIQUID_GLASS_CONFIG_KEY).isFlagEnabled(SMOOTH_PAGE_SWITCH)) {
            return
        }
        val pager = activePager() ?: return
        ensureNeighborPagesPreloaded(pager)
        val arm = SmoothArm(
            pager = pager,
            target = index,
            expiresAt = SystemClock.uptimeMillis() + SMOOTH_ARM_TIMEOUT_MS,
        )
        armedSmoothTarget = arm
        pager.postDelayed({
            if (armedSmoothTarget === arm && SystemClock.uptimeMillis() >= arm.expiresAt) {
                armedSmoothTarget = null
            }
        }, SMOOTH_ARM_TIMEOUT_MS)
    }

    /** 底栏切换结束时清理已消费或已过期授权；未消费授权留给下一帧。 */
    fun clearSmoothTarget() {
        val arm = armedSmoothTarget ?: return
        if (arm.expiresAt <= SystemClock.uptimeMillis()) armedSmoothTarget = null
    }

    /**
     * 底栏当前选中槽位：优先读子项选中态，其次反射调用 `getCurrentTab()`。
     *
     * 仅 Material 风格底栏实现了该 getter；`TabWidget` 系底栏没有，
     * 于是落入子项选中态这一信号，两者的数据源本就一致。
     */
    fun currentIndex(tabView: View): Int {
        val row = tabView as? ViewGroup
        val selected = selectedIndex(findTabRow(row))
        if (selected >= 0) return selected
        return runCatching {
            tabView.javaClass.getMethod("getCurrentTab").invoke(tabView) as? Int
        }.getOrNull()?.let { slotForIndex(row, it) } ?: -1
    }

    /**
     * 为背景页面容器安装平滑切页钩子。
     *
     * 底栏触发的切换统一交给 ViewPager2 的平滑路径处理；页间距离不再限制为 1，
     * pager 正在移动时也继续传递 `setCurrentItem(index, true)`，由 ViewPager2
     * 自己重新定位当前动画目标，避免中途硬切造成回退。切换前仅把相邻页预加载，
     * 远距离页仍由 ViewPager2 按需创建。
     */
    fun tryHookPager(pager: ViewGroup?) {
        if (!TCQTSetting.getInt(LIQUID_GLASS_CONFIG_KEY).isFlagEnabled(SMOOTH_PAGE_SWITCH)) return
        if (pager == null) return
        var hookedClass: Class<*>? = null
        runCatching {
            var method: Method? = null
            var cls: Class<*>? = pager.javaClass
            while (cls != null && cls != Any::class.java) {
                method = runCatching {
                    cls.getDeclaredMethod(
                        "setCurrentItem",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    )
                }.getOrNull()
                if (method != null) break
                cls = cls.superclass
            }
            val target = method ?: return@runCatching Log.w(
                "页容器上无 setCurrentItem(int, boolean)，切页将保持硬切"
            )
            val hookClass = target.declaringClass
            hookedClass = hookClass
            ensureNeighborPagesPreloaded(pager)
            if (!pagerHookedClasses.add(hookClass)) return@runCatching
            target.hookReplace { chain ->
                val requested = chain.args.getOrNull(0) as? Int
                val arm = armedSmoothTarget
                if (arm == null || arm.expiresAt <= SystemClock.uptimeMillis()) {
                    if (arm != null) armedSmoothTarget = null
                    return@hookReplace chain.proceed()
                }
                if (chain.thisObject !== arm.pager || !isCurrentPager(chain.thisObject) ||
                    requested != arm.target
                ) return@hookReplace chain.proceed()

                armedSmoothTarget = null
                if (chain.args.getOrNull(1) == false) chain.args[1] = true
                chain.proceed()
            }
            Log.i("已挂钩 ${target.declaringClass.name}.setCurrentItem(int, boolean) 用于安全平滑切页")
        }.onFailure {
            hookedClass?.let(pagerHookedClasses::remove)
            Log.w("平滑切页钩子安装失败: $it")
        }
    }

    private fun isCurrentPager(pager: Any): Boolean =
        pager === activePager()

    private fun activePager(): ViewGroup? {
        return if (FloatingBottomBarConfigStore.read().implementation == BottomBarImplementation.NEW_VIEW) {
            NewViewBarInstaller.currentPager()
        } else {
            GlassBarInstaller.currentPager()
        }
    }

    /** 只保证相邻页已预加载，避免把全部 Tab 页提前实例化。 */
    private fun ensureNeighborPagesPreloaded(pager: ViewGroup) {
        runCatching {
            val currentLimit = pager.javaClass.methods.firstOrNull {
                it.name == "getOffscreenPageLimit" && it.parameterTypes.isEmpty()
            }?.invoke(pager) as? Int
            if (currentLimit != null && currentLimit >= 1) return@runCatching

            val setter = pager.javaClass.methods.firstOrNull {
                it.name == "setOffscreenPageLimit" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: return@runCatching
            setter.invoke(pager, 1)
            Log.i("平滑切页已预加载相邻页: offscreenPageLimit=1")
        }.onFailure { Log.w("相邻页预加载设置失败，保持宿主默认离屏策略: $it") }
    }

}
