package com.owo233.tcqt.features.appearance.liquidglass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.withTranslation
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import kotlin.math.abs
import kotlin.math.max

/**
 * 浮动玻璃栏的宿主容器。
 *
 * 三个职责：其一，以自绘投影替代 `setElevation`——宿主视图树会吞掉平台阴影，
 * 自绘才能保证阴影始终与药丸外形吻合；其二，接管触摸分发——拦截横向拖拽驱动
 * 液滴，阻止外层抽屉抢占手势，纵向滑动在底栏上滑回调所需行程后放行；
 * 其三，探测深浅色主题——QQ 皮肤引擎的夜间模式独立于系统 uiMode，
 * 以未选中标签的实际文字色判定。
 */
@SuppressLint("ViewConstructor")
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
        fun onTheme(theme: BottomBarTheme)
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
    private var theme = bar?.let(BottomBarTheme::fromNative) ?: BottomBarTheme.default(HookEnv.isNightMode())

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
        isDarkTheme = theme.dark
        // Log.i("玻璃容器创建: dark=$isDarkTheme 信号=${if (probe != null) "标签色投票" else "宿主主题"}")
        tag = GLASS_HOST_TAG
        setWillNotDraw(false)
    }

    /** 挂载渲染层回调，并立即以当前主题初始化渲染层。 */
    fun setRendererCallback(callback: RendererCallback) {
        renderer = callback
        callback.onTheme(theme)
    }

    fun setDragHandler(handler: DragHandler) {
        dragHandler = handler
    }

    /**
     * 配置自绘投影：四周预留 14dp，由带 shadowLayer 的圆角矩形绘制。
     *
     * 不提升图层类型——把容器整体纹理化会把预留内边距区域一并渲染，在背后的
     * 列表分隔线上留下矩形残影。
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
        // KernelSU's CircleShape uses an equal-radius ellipse: half of the
        // actual bar height, with no width-dependent cap.
        val cornerRadius = (h - shadowPad * 2).coerceAtLeast(0) * 0.5f
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
     * 玻璃覆盖投影的滞后一帧（宿主滑出底栏期间）会在药丸底部闪出黑色条带。
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        protectGestureFromAncestors(event)
        return dragHandler?.onTouch(event) == true || super.onTouchEvent(event)
    }

    /**
     * 阻止外层抽屉或页容器抢占始于玻璃栏的拖拽。
     *
     * 直接请求父级而非自身：在本容器上也设置该标志会让自身的
     * `onInterceptTouchEvent` 收不到触发液滴拖拽的 MOVE 事件。
     * 点击交由宿主 Tab 子项处理；明显的纵向手势在约 50px 行程之后放行。
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
     * 主题回调负责重装；定期复核同时覆盖宿主异步更新的标签色。
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
        if (bar?.visibility != View.VISIBLE) return
        themeProbeCount++
        if (themeProbeCount % PROBE_INTERVAL != 1) return
        val detected = BottomBarTheme.darkFromTabs(bar) ?: theme.dark
        if (detected != isDarkTheme) {
            isDarkTheme = detected
            theme = BottomBarTheme.default(detected)
            applyShadowColour(detected)
            renderer?.onTheme(theme)
            invalidate()
            Log.i("玻璃主题切换: dark=$detected")
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
