package com.owo233.tcqt.hooks.func.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.view.animation.OvershootInterpolator
import androidx.core.view.children
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.utils.log.Log
import java.util.HashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.withTranslation

/**
 * 浮动玻璃栏的宿主容器。
 *
 * 职责有三：其一，以自绘投影替代 `setElevation`——宿主视图树会吞掉平台
 * 阴影，自绘则能保证阴影始终与药丸外形吻合；其二，接管触摸分发——
 * 拦截横向拖拽以驱动液滴，同时阻止外层抽屉抢占手势，纵向滑动则在
 * 底栏自身的上滑回调所需的行程后放行；其三，探测深浅色主题——
 * QQ 的皮肤引擎拥有独立于系统 uiMode 的夜间模式，标签文字颜色才是
 * 玻璃配色需要跟随的真实信号，以全部标签颜色的众数投票判定。
 */
internal class GlassBarHostLayout(
    context: Context,
    /** 玻璃折射的背景页面容器。 */
    val sampleRoot: ViewGroup,
    /** 宿主原生底栏，保留引用以便持续读取其标签颜色探测主题。 */
    private val bar: ViewGroup?,
) : FrameLayout(context) {

    /** 渲染层回调：尺寸与主题变化时通知玻璃面板与液滴同步。 */
    interface RendererCallback {
        fun onSize(width: Int, height: Int, cornerRadius: Float)
        fun onTheme(dark: Boolean)
    }

    /** 手势接管方：优先获得触摸事件，但只应认领横向拖拽。 */
    interface DragHandler {
        fun onIntercept(event: MotionEvent): Boolean
        fun onTouch(event: MotionEvent): Boolean
    }

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** 已解析的深色主题状态。 */
    var isDarkTheme: Boolean
        private set

    private var renderer: RendererCallback? = null
    private var dragHandler: DragHandler? = null

    // ---- 自绘投影 ----

    /** 四周为投影预留的内边距，子视图（MATCH_PARENT）收缩进内框。 */
    var shadowPad = 0
        private set

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowClip = Path()
    private var shadowClipW = -1
    private var shadowClipH = -1
    private var shadowOffsetY = 0f
    private var shadowHidden = false
    private var shadowAlpha = 255

    /** 主题轮询计数：随绘制周期递增，周期性复核皮肤切换。 */
    private var themeProbeCount = 0

    /** 手势保护状态。 */
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var ancestorsBlocked = false

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    init {
        val probe = detectDarkFromText(bar)
        isDarkTheme = probe ?: HookEnv.isNightMode()
        Log.i("玻璃容器创建: dark=$isDarkTheme 信号=${if (probe != null) "标签色投票" else "宿主主题"}")
        setTag(GLASS_HOST_TAG)
        setWillNotDraw(false)
    }

    /** 挂载渲染层回调，并立即以当前主题初始化渲染层。 */
    fun setRendererCallback(callback: RendererCallback) {
        renderer = callback
        callback.onTheme(isDarkTheme)
    }

    fun setDragHandler(handler: DragHandler) {
        dragHandler = handler
    }

    /**
     * 配置自绘投影：四周预留 14dp，由带 shadowLayer 的圆角矩形绘制。
     *
     * 不提升图层类型——把容器整体纹理化会把预留内边距区域一并渲染，
     * 在背后的列表分隔线上留下矩形残影；API 28 起 drawRoundRect 的
     * 投影层本身已走硬件加速。
     */
    fun setupShadow(dark: Boolean) {
        shadowPad = (density * 14f).toInt()
        setPadding(shadowPad, shadowPad, shadowPad, shadowPad)
        shadowPaint.color = Color.BLACK
        applyShadowColour(dark)
        invalidate()
    }

    private fun applyShadowColour(dark: Boolean) {
        shadowPaint.setShadowLayer(density * 10f, 0f, density * 2f, if (dark) 0x33000000 else 0x1A000000)
    }

    /** 随宿主底栏的平移与淡出联动投影。 */
    fun setShadowOffsetY(translationY: Float, alpha: Float) {
        val hidden = translationY == Float.MAX_VALUE
        val newAlpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        if (shadowHidden == hidden && shadowOffsetY == translationY && shadowAlpha == newAlpha) return
        shadowHidden = hidden
        shadowOffsetY = if (hidden) 0f else translationY
        shadowAlpha = newAlpha
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val cornerRadius = min(h * 0.46f, 30f * density)
        // 玻璃层位于投影内边距之内，回调时扣除该内边距。
        renderer?.onSize(w - shadowPad * 2, h - shadowPad * 2, cornerRadius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPillShadow(canvas)
    }

    /**
     * 绘制药丸投影，并从投影中挖去药丸本身。
     *
     * 填充色仅用于投影成形，若依赖玻璃覆盖它，玻璃滞后的一帧
     * （宿主滑出底栏期间）会在药丸底部闪出黑色条带。
     */
    private fun drawPillShadow(canvas: Canvas) {
        if (shadowPad <= 0 || shadowHidden || shadowAlpha == 0) return
        val left = shadowPad.toFloat()
        val top = shadowPad.toFloat()
        val right = (width - shadowPad).toFloat()
        val bottom = (height - shadowPad).toFloat()
        if (right <= left || bottom <= top) return
        val radius = (bottom - top) * 0.5f

        canvas.withTranslation(0f, shadowOffsetY) {
            if (shadowClipW != width || shadowClipH != height) {
                shadowClip.reset()
                shadowClip.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW)
                shadowClipW = width
                shadowClipH = height
            }
            canvas.clipOutPath(shadowClip)
            val previous = shadowPaint.alpha
            shadowPaint.alpha = shadowAlpha
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, shadowPaint)
            shadowPaint.alpha = previous
        }
    }

    // ---- 手势分发 ----

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        protectGestureFromAncestors(event)
        return dragHandler?.onIntercept(event) == true || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        protectGestureFromAncestors(event)
        return dragHandler?.onTouch(event) == true || super.onTouchEvent(event)
    }

    /**
     * 阻止外层抽屉或页容器抢占始于玻璃栏的拖拽。
     *
     * 直接请求父级而非自身：在本容器上也设置该标志会令自身的
     * `onInterceptTouchEvent` 收不到触发液滴拖拽的 MOVE 事件。
     * 点击仍交由宿主 Tab 子项处理；明显的纵向手势在底栏原生上滑
     * 回调所需的行程（约 50px）之后放行。
     */
    private fun protectGestureFromAncestors(event: MotionEvent) {
        val parent = parent ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureDownX = event.x
                gestureDownY = event.y
                ancestorsBlocked = true
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> if (ancestorsBlocked) {
                val dx = event.x - gestureDownX
                val dy = event.y - gestureDownY
                val verticalRelease = max(touchSlop.toFloat(), 50f)
                if (abs(dy) > verticalRelease && abs(dy) > abs(dx)) {
                    ancestorsBlocked = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (ancestorsBlocked) {
                ancestorsBlocked = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    // ---- 主题探测 ----

    /**
     * 开始周期性主题复核：宿主的皮肤切换没有任何回调可订阅，
     * 只能在每帧绘制前轮询，以计数节流。
     */
    fun attachThemeProbe() {
        detachThemeProbe()
        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (isAttachedToWindow) refreshThemeIfDue()
            true
        }
        sampleRoot.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        invalidate()
        playRevealAnimation()
    }

    fun detachThemeProbe() {
        preDrawListener?.let { listener ->
            runCatching { sampleRoot.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
        preDrawListener = null
    }

    override fun onDetachedFromWindow() {
        detachThemeProbe()
        super.onDetachedFromWindow()
    }

    private fun refreshThemeIfDue() {
        themeProbeCount++
        if (themeProbeCount % PROBE_INTERVAL != 1) return
        val detected = detectDarkFromText(bar) ?: HookEnv.isNightMode()
        if (detected != isDarkTheme) {
            isDarkTheme = detected
            applyShadowColour(detected)
            renderer?.onTheme(detected)
            invalidate()
            Log.i("玻璃主题切换: dark=$detected")
        }
    }

    /**
     * 以标签文字颜色的众数判定主题。
     *
     * 标签子树中混有未读角标（红底白字），首个命中会被其污染；
     * 众数投票可规避——未选中颜色始终占据多数席位，角标与单个
     * 选中标签都赢不了投票。明亮文字意味着深色底栏。
     */
    private fun detectDarkFromText(root: ViewGroup?): Boolean? {
        if (root == null) return null
        return runCatching {
            val votes = HashMap<Int, Int>()
            collectTextColors(root, votes)
            val best = votes.maxByOrNull { it.value }?.key ?: return null
            val luminance = (0.299f * Color.red(best) +
                0.587f * Color.green(best) +
                0.114f * Color.blue(best)) / 255f
            luminance > 0.5f
        }.getOrNull()
    }

    private fun collectTextColors(view: View, votes: MutableMap<Int, Int>) {
        if (view.visibility != VISIBLE) return
        if (view is TextView) {
            // 角标带背景drawable，普通标签没有，以此区分。
            if (view.background == null && !view.text.isNullOrEmpty()) {
                view.textColors?.defaultColor?.or(0xFF000000.toInt())?.let { colour ->
                    votes.merge(colour, 1, Int::plus)
                }
            }
            return
        }
        if (view is ViewGroup) {
            for (child in view.children) collectTextColors(child, votes)
        }
    }

    // ---- 入场动画 ----

    /** 自底部的回弹展开：以底边中点为轴心纵向缩放并淡入。 */
    private fun playRevealAnimation() {
        runCatching {
            pivotX = width * 0.5f
            pivotY = height.toFloat()
            scaleY = 0.86f
            alpha = 0f
            animate().alpha(1f).scaleY(1f)
                .setDuration(380L)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    private companion object {
        /** 每隔该数量的绘制周期复核一次主题。 */
        const val PROBE_INTERVAL = 20

        val GLASS_HOST_TAG = Any()
    }
}
