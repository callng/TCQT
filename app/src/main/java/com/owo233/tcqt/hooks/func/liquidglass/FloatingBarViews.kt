package com.owo233.tcqt.hooks.func.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.withClip

/** Logical dimensions shared by the runtime bar and the settings preview. */
internal data class FloatingBarGeometry(
    val totalWidth: Int,
    val barHeight: Int,
    val tabWidth: Int,
    val tabHeight: Int,
    val horizontalPadding: Int,
    val shadowPadding: Int,
    val scale: Float,
) {
    companion object {
        fun fromBase(
            baseBarHeight: Int,
            baseTabWidth: Int,
            baseTabHeight: Int,
            baseShadowPadding: Int,
            tabCount: Int,
            scale: Float,
        ): FloatingBarGeometry {
            val safeScale = scale.coerceIn(0.8f, 1.2f)
            val safeTabCount = tabCount.coerceAtLeast(1)
            val scaledTabWidth = (baseTabWidth * safeScale).roundToInt().coerceAtLeast(1)
            val scaledPadding = (4f * safeScale).roundToInt().coerceAtLeast(1)
            return FloatingBarGeometry(
                // Derive the total from slots instead of scaling it
                // independently. Independent rounding leaves a one-pixel
                // remainder on one side at common scale values.
                totalWidth = scaledTabWidth * safeTabCount + scaledPadding * 2,
                barHeight = (baseBarHeight * safeScale).roundToInt(),
                tabWidth = scaledTabWidth,
                tabHeight = (baseTabHeight * safeScale).roundToInt(),
                horizontalPadding = scaledPadding,
                shadowPadding = (baseShadowPadding * safeScale).roundToInt(),
                scale = safeScale,
            )
        }
    }
}

/** View-based surface used by NewView in both normal and reduced-glass modes. */
internal class FloatingBarSurfaceView(
    context: Context,
    private var mode: FloatingBottomBarMode,
    private var dark: Boolean,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun update(mode: FloatingBottomBarMode, dark: Boolean) {
        if (this.mode == mode && this.dark == dark) return
        this.mode = mode
        this.dark = dark
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height * 0.5f
        val base = if (dark) Color.rgb(43, 43, 46) else Color.rgb(246, 246, 250)
        val alpha = if (mode == FloatingBottomBarMode.LIQUID_GLASS) 0xD0 else 0xF2
        paint.color = (alpha shl 24) or (base and 0x00FFFFFF)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)

        border.strokeWidth = max(1f, resources.displayMetrics.density)
        border.color = if (dark) 0x48FFFFFF else 0x55000000
        val borderRadius = max(0f, radius - border.strokeWidth * 0.5f)
        canvas.drawRoundRect(
            border.strokeWidth * 0.5f,
            border.strokeWidth * 0.5f,
            width - border.strokeWidth * 0.5f,
            height - border.strokeWidth * 0.5f,
            borderRadius,
            borderRadius,
            border,
        )
    }
}

/** Selected-item renderer shared by NewView runtime and the preview. */
internal class FloatingBarIndicatorView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dark = false

    fun setTheme(dark: Boolean) {
        this.dark = dark
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = if (dark) 0xFF56565B.toInt() else 0xFFE0E4EA.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height * 0.5f, height * 0.5f, paint)
    }
}

/** A bounded preview item with the same icon/text grouping as a native tab. */
private class FloatingBarPreviewItem(context: Context, iconKind: IconKind, title: String) : LinearLayout(context) {
    private val icon = PreviewTabIconView(context, iconKind)
    private val label = TextView(context)
    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, 0)
        icon.layoutParams = LayoutParams((24f * density).roundToInt(), (24f * density).roundToInt())
        label.text = title
        label.gravity = android.view.Gravity.CENTER
        label.isSingleLine = true
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        label.includeFontPadding = false
        label.layoutParams = LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4f * density).roundToInt()
        }
        addView(icon)
        addView(label)
    }

    fun update(scale: Float, dark: Boolean, selected: Boolean, liquid: Boolean = false) {
        val safeScale = scale.coerceIn(0.8f, 1.2f)
        val colour = if (selected) {
            if (liquid) 0xFF0A84FF.toInt() else if (dark) Color.WHITE else 0xFF1B1D22.toInt()
        } else if (dark) {
            0xFFD0D0D5.toInt()
        } else {
            0xFF5F626B.toInt()
        }
        icon.setTint(colour)
        label.setTextColor(colour)
        isSelected = selected
        label.textSize = 11f * safeScale
        val iconSize = (24f * density * safeScale).roundToInt()
        (icon.layoutParams as? LayoutParams)?.let {
            it.width = iconSize
            it.height = iconSize
            icon.layoutParams = it
        }
    }
}

private enum class IconKind { MESSAGE, CHANNEL, CONTACT, ACTIVITY }

/** Small vector-like icons matching QQ's four bottom destinations. */
private class PreviewTabIconView(context: Context, private val kind: IconKind) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private var tint = Color.WHITE

    fun setTint(value: Int) {
        if (tint == value) return
        tint = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val cx = width * 0.5f
        val cy = height * 0.5f
        val r = 8f * d
        paint.color = tint
        paint.style = Paint.Style.STROKE
        when (kind) {
            IconKind.MESSAGE -> {
                val bubble = android.graphics.RectF(cx - 9f * d, cy - 8f * d, cx + 9f * d, cy + 6f * d)
                canvas.drawRoundRect(bubble, 7f * d, 7f * d, paint)
                canvas.drawLine(cx - 4f * d, cy + 6f * d, cx - 7f * d, cy + 10f * d, paint)
            }
            IconKind.CHANNEL -> {
                paint.strokeWidth = 2.2f * d
                canvas.drawLine(cx - 5f * d, cy - 10f * d, cx - 2f * d, cy + 10f * d, paint)
                canvas.drawLine(cx + 5f * d, cy - 10f * d, cx + 2f * d, cy + 10f * d, paint)
                canvas.drawLine(cx - 9f * d, cy - 3f * d, cx + 9f * d, cy - 3f * d, paint)
                canvas.drawLine(cx - 10f * d, cy + 4f * d, cx + 8f * d, cy + 4f * d, paint)
            }
            IconKind.CONTACT -> {
                canvas.drawCircle(cx, cy - 5f * d, r * 0.55f, paint)
                canvas.drawArc(cx - 9f * d, cy + 1f * d, cx + 9f * d, cy + 12f * d, 180f, 180f, false, paint)
            }
            IconKind.ACTIVITY -> {
                canvas.drawArc(cx - 9f * d, cy - 9f * d, cx + 9f * d, cy + 9f * d, -55f, 285f, false, paint)
                val arrow = Path().apply {
                    moveTo(cx + 7f * d, cy - 9f * d)
                    lineTo(cx + 10f * d, cy - 2f * d)
                    lineTo(cx + 3f * d, cy - 2f * d)
                }
                canvas.drawPath(arrow, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx + 5f * d, cy - 8f * d, 2f * d, paint)
            }
        }
    }
}

/**
 * Host for NewView. Persistent scaling changes this view's measured geometry;
 * the transient press animation from [DropletGestureDriver] may still scale it
 * briefly while a gesture is active.
 */
internal class FloatingBarHostLayout(
    context: Context,
) : FrameLayout(context), GlassBarHostLayout.DragHandler {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPath = Path()
    private var shadowClipWidth = -1
    private var shadowClipHeight = -1
    private var dragHandler: GlassBarHostLayout.DragHandler? = null
    private var downX = 0f
    private var downY = 0f
    private var ancestorsBlocked = false
    private var baseShadowPadding = (14f * density).roundToInt()

    var isDarkTheme: Boolean = runCatching { com.owo233.tcqt.HookEnv.isNightMode() }.getOrDefault(false)
        private set
    var shadowPadding: Int = baseShadowPadding
        private set

    init {
        setWillNotDraw(false)
        shadowPaint.color = Color.BLACK
        applyShadowColour()
        clipChildren = false
        clipToPadding = false
    }

    fun setupShadow() {
        applyShadowColour()
        setGeometryScale(1f)
    }

    fun setDarkTheme(dark: Boolean) {
        if (isDarkTheme == dark) return
        isDarkTheme = dark
        applyShadowColour()
        invalidate()
    }

    fun setDragHandler(handler: GlassBarHostLayout.DragHandler?) {
        dragHandler = handler
    }

    override fun onIntercept(event: MotionEvent): Boolean = dragHandler?.onIntercept(event) == true

    override fun onTouch(event: MotionEvent): Boolean = dragHandler?.onTouch(event) == true

    fun setGeometryScale(scale: Float) {
        val safeScale = scale.coerceIn(0.8f, 1.2f)
        shadowPadding = (baseShadowPadding * safeScale).roundToInt()
        shadowClipWidth = -1
        shadowClipHeight = -1
        setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
        invalidate()
        requestLayout()
    }

    private fun applyShadowColour() {
        shadowPaint.setShadowLayer(
            10f * density,
            0f,
            2f * density,
            if (isDarkTheme) 0x33000000 else 0x1A000000,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shadowPadding <= 0) return
        val left = shadowPadding.toFloat()
        val top = shadowPadding.toFloat()
        val right = width - shadowPadding.toFloat()
        val bottom = height - shadowPadding.toFloat()
        if (right <= left || bottom <= top) return
        val radius = (bottom - top) * 0.5f
        if (shadowClipWidth != width || shadowClipHeight != height) {
            shadowPath.reset()
            shadowPath.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW)
            shadowClipWidth = width
            shadowClipHeight = height
        }
        canvas.save()
        canvas.clipOutPath(shadowPath)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, shadowPaint)
        canvas.restore()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        protectAncestors(event)
        return onIntercept(event) || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        protectAncestors(event)
        return onTouch(event) || super.onTouchEvent(event)
    }

    private fun protectAncestors(event: MotionEvent) {
        val parent = parent ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                ancestorsBlocked = true
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (ancestorsBlocked) {
                val dx = event.x - downX
                val dy = event.y - downY
                if (kotlin.math.abs(dy) > max(touchSlop.toFloat(), 50f) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
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
}

/** Preview that exercises the same surface and liquid-glass renderers as NewView. */
internal class FloatingBottomBarPreviewView(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val baseBarHeight = (64f * density).roundToInt()
    private val baseTabWidth = (76f * density).roundToInt()
    private val backdrop = FrameLayout(context)
    private val barHost = FrameLayout(context)
    private val pagePreview = PreviewPageView(context)
    private val normalSurface = FloatingBarSurfaceView(context, FloatingBottomBarMode.NORMAL, false)
    private val normalIndicator = FloatingBarIndicatorView(context)
    private val glassSurface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GlassPillView(context, backdrop, density, false)
    } else null
    private val glassIndicator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GlassDropletView(context, backdrop, null, density, false)
    } else null
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val labels = listOf("消息", "频道", "联系人", "动态")
    private val iconKinds = listOf(IconKind.MESSAGE, IconKind.CHANNEL, IconKind.CONTACT, IconKind.ACTIVITY)
    private var scale = 1f
    private var mode = FloatingBottomBarMode.NORMAL
    private var dark = false
    private var blurPercent = 100
    private var position = FloatingBottomBarPosition.MODERATE
    private var selected = 1
    private var downX = 0f
    private var dragging = false
    private var barLeft = 0
    private var barTop = 0
    private var barWidth = 0
    private var barHeight = 0
    private var activeIndicator: View = normalIndicator
    private var liquidDriver: DropletGestureDriver? = null
    private var liquidDragging = false
    private var geometry = FloatingBarGeometry.fromBase(
        baseBarHeight = baseBarHeight,
        baseTabWidth = baseTabWidth,
        baseTabHeight = baseBarHeight,
        baseShadowPadding = 0,
        tabCount = 4,
        scale = 1f,
    )

    init {
        backdrop.clipChildren = false
        barHost.clipChildren = false
        barHost.clipToPadding = false
        backdrop.addView(pagePreview, LayoutParams(-1, -1))
        addView(backdrop)
        addView(barHost)
        // Match the runtime z-order: native Tab content is beneath the
        // selected indicator so Liquid Glass can redraw the active item.
        labels.forEachIndexed { index, label ->
            row.addView(FloatingBarPreviewItem(context, iconKinds[index], label))
        }
        barHost.addView(row)
        barHost.addView(normalSurface)
        barHost.addView(normalIndicator)
        glassSurface?.let { barHost.addView(it) }
        glassIndicator?.let { barHost.addView(it) }
        glassIndicator?.setTabRow(row)
        glassIndicator?.setPill(glassSurface)
        liquidDriver = glassIndicator?.let { indicator ->
            DropletGestureDriver(indicator, row, density).apply {
                setPill(glassSurface)
                setHost(barHost)
                setTabRow(row)
                animateToIndex(selected, true)
            }
        }
        setRenderer(FloatingBottomBarMode.NORMAL)
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun update(
        scale: Float,
        mode: FloatingBottomBarMode,
        dark: Boolean,
        blurPercent: Int = 100,
        position: Int = FloatingBottomBarPosition.MODERATE.storedValue,
    ) {
        this.scale = scale.coerceIn(0.8f, 1.2f)
        this.mode = mode
        this.dark = dark
        this.blurPercent = blurPercent.coerceIn(0, 100)
        this.position = if (position == FloatingBottomBarPosition.BOTTOM.storedValue) {
            FloatingBottomBarPosition.BOTTOM
        } else {
            FloatingBottomBarPosition.MODERATE
        }
        pagePreview.setDark(dark)
        setRenderer(mode)
        normalSurface.update(FloatingBottomBarMode.NORMAL, dark)
        normalIndicator.setTheme(dark)
        glassSurface?.let {
            it.setTheme(dark)
            it.setBlurPercent(this.blurPercent)
        }
        glassIndicator?.let {
            it.setTheme(dark)
            it.setBlurPercent(this.blurPercent)
        }
        labels.indices.forEach { index ->
            (row.getChildAt(index) as? FloatingBarPreviewItem)?.update(
                this.scale, dark, index == selected, mode == FloatingBottomBarMode.LIQUID_GLASS,
            )
        }
        requestLayout()
        invalidate()
    }

    private fun setRenderer(requestedMode: FloatingBottomBarMode) {
        val useGlass = requestedMode == FloatingBottomBarMode.LIQUID_GLASS &&
            glassSurface != null && glassIndicator != null
        activeIndicator = if (useGlass) glassIndicator else normalIndicator
        activeIndicator.animate().cancel()
        normalSurface.visibility = if (useGlass) GONE else VISIBLE
        normalIndicator.visibility = if (useGlass) GONE else VISIBLE
        glassSurface?.visibility = if (useGlass) VISIBLE else GONE
        glassIndicator?.visibility = if (useGlass) VISIBLE else GONE
        if (useGlass) {
            // Surface below the native content, selected indicator above it.
            row.bringToFront()
            glassIndicator.bringToFront()
            liquidDriver?.animateToIndex(selected, true)
        } else {
            row.bringToFront()
            liquidDragging = false
            liquidDriver?.animateToIndex(selected, true)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(0)
        geometry = FloatingBarGeometry.fromBase(
            baseBarHeight = baseBarHeight,
            baseTabWidth = baseTabWidth,
            baseTabHeight = baseBarHeight,
            baseShadowPadding = 0,
            tabCount = labels.size,
            scale = scale,
        )
        val outerPad = geometry.horizontalPadding.coerceAtLeast(1)
        val maxWidth = max(0, width - (56f * density).roundToInt())
        val desiredWidth = min(maxWidth, geometry.totalWidth)
        val tabWidth = max(1, (desiredWidth - outerPad * 2) / labels.size)
        barWidth = min(width, tabWidth * labels.size + outerPad * 2)
        barHeight = geometry.barHeight
        val innerWidth = max(1, barWidth - outerPad * 2)
        val innerHeight = max(1, barHeight - outerPad * 2)
        // Leave a visible lower bezel below the bar so the phone outline remains readable.
        val previewHeight = max(
            (220f * density).roundToInt(),
            barHeight + (52f * density).roundToInt(),
        )
        setMeasuredDimension(width, previewHeight)
        backdrop.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY))
        barHost.measure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY))
        normalSurface.measure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY))
        normalIndicator.measure(MeasureSpec.makeMeasureSpec(tabWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY))
        glassSurface?.measure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY))
        glassIndicator?.measure(MeasureSpec.makeMeasureSpec(tabWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY))
        row.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY))
        row.children.forEach { child ->
            val lp = child.layoutParams
            lp.width = tabWidth
            lp.height = innerHeight
            (lp as? LinearLayout.LayoutParams)?.weight = 0f
            child.layoutParams = lp
            child.measure(MeasureSpec.makeMeasureSpec(tabWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        backdrop.layout(0, 0, width, height)
        barLeft = (width - barWidth) / 2
        // 预览无导航栏，直接用无导航栏的偏移。
        val bottomOffset = position.offsetDp(hasNavBar = false)
        barTop = height - barHeight - (bottomOffset * density).roundToInt()
        barTop = barTop.coerceAtLeast(0)
        barHost.layout(barLeft, barTop, barLeft + barWidth, barTop + barHeight)
        normalSurface.layout(0, 0, barWidth, barHeight)
        glassSurface?.layout(0, 0, barWidth, barHeight)
        val pad = geometry.horizontalPadding.coerceAtLeast(1)
        val tabWidth = normalIndicator.measuredWidth
        // Keep both indicators anchored to the first slot; the runtime driver
        // moves the selected pill with translationX.
        val indicatorLeft = pad
        row.layout(pad, pad, pad + row.measuredWidth, pad + row.measuredHeight)
        normalIndicator.layout(indicatorLeft, pad, indicatorLeft + tabWidth, pad + normalIndicator.measuredHeight)
        glassIndicator?.layout(indicatorLeft, pad, indicatorLeft + tabWidth, pad + glassIndicator.measuredHeight)
        normalIndicator.translationX = selected * tabWidth.toFloat()
        glassIndicator?.let {
            it.translationX = selected * tabWidth.toFloat()
            liquidDriver?.animateToIndex(selected, true)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (mode == FloatingBottomBarMode.LIQUID_GLASS && liquidDriver != null) {
            return handleLiquidTouch(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y < barTop || event.y > barTop + barHeight) return false
                downX = event.x
                dragging = false
                activeIndicator.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > ViewConfiguration.get(context).scaledTouchSlop) dragging = true
                if (dragging) selectAt(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) selectAt(event.x)
                activeIndicator.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
                dragging = false
                return true
            }
        }
        return true
    }

    private fun handleLiquidTouch(event: MotionEvent): Boolean {
        val driver = liquidDriver ?: return false
        event.offsetLocation(-barLeft.toFloat(), -barTop.toFloat())
        try {
            return handleLiquidTouchLocal(driver, event)
        } finally {
            event.offsetLocation(barLeft.toFloat(), barTop.toFloat())
        }
    }

    private fun handleLiquidTouchLocal(driver: DropletGestureDriver, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y < 0f || event.y > barHeight) return false
                liquidDragging = false
                driver.onIntercept(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (driver.onIntercept(event)) liquidDragging = true
                if (liquidDragging) driver.onTouch(event)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = liquidDragging
                driver.onIntercept(event)
                if (wasDragging) driver.onTouch(event)
                selectAt(event.x + barLeft)
                liquidDragging = false
                return true
            }
        }
        return true
    }

    private fun selectAt(x: Float) {
        val tabWidth = activeIndicator.measuredWidth
        if (tabWidth <= 0) return
        val previous = selected
        selected = ((x - barLeft - (4f * density * scale)) / tabWidth).toInt().coerceIn(0, labels.lastIndex)
        if (selected == previous) return
        labels.indices.forEach { index ->
            (row.getChildAt(index) as? FloatingBarPreviewItem)?.update(
                scale, dark, index == selected, mode == FloatingBottomBarMode.LIQUID_GLASS,
            )
        }
        if (mode == FloatingBottomBarMode.LIQUID_GLASS) {
            liquidDriver?.animateToIndex(selected, false)
            invalidate()
            return
        }
        val oldLeft = geometry.horizontalPadding + previous * tabWidth
        val newLeft = geometry.horizontalPadding + selected * tabWidth
        activeIndicator.animate().cancel()
        activeIndicator.translationX = (oldLeft - newLeft).toFloat()
        activeIndicator.post {
            activeIndicator.animate().translationX(0f).setDuration(220L).setInterpolator(
                android.view.animation.DecelerateInterpolator(),
            ).start()
        }
        invalidate()
    }

    /** Resolve both taps and drags from the laid-out bar-local geometry. */
}

/** A restrained QQ-like page backdrop used by the real glass capture pipeline. */
private class PreviewPageView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePath = Path()
    private var dark = false

    fun setDark(value: Boolean) {
        if (dark == value) return
        dark = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val side = (14f * density).coerceAtMost(w * 0.12f)
        val top = (14f * density)
        val bottom = h - (8f * density)
        val radius = min(32f * density, (bottom - top) * 0.5f)
        val frame = android.graphics.RectF(side, top, w - side, bottom)
        framePath.reset()
        framePath.addRoundRect(frame, radius, radius, Path.Direction.CW)
        paint.color = if (dark) 0xFF242528.toInt() else 0xFFE9EBEF.toInt()
        canvas.withClip(framePath) {
            drawRoundRect(frame, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density.toFloat()
            paint.color = if (dark) 0x336B6D72 else 0x33484B52
            drawRoundRect(frame, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = if (dark) 0x18FFFFFF else 0x18000000
        canvas.drawRoundRect(side + 10f * density, top + 16f * density, w - side - 10f * density, top + 40f * density, 12f * density, 12f * density, paint)
        paint.color = if (dark) 0x22FFFFFF else 0x22000000
        val lineLeft = side + 16f * density
        val lineRight = w - side - 16f * density
        for (index in 0..3) {
            val y = top + (58f + index * 23f) * density
            canvas.drawRoundRect(lineLeft, y, lineRight, y + 9f * density, 4.5f * density, 4.5f * density, paint)
        }
        paint.color = if (dark) 0x12FFFFFF else 0x12000000
        val cardTop = min(top + 158f * density, bottom - 50f * density)
        val cardBottom = min(cardTop + 44f * density, bottom - 12f * density)
        canvas.drawRoundRect(lineLeft, cardTop, w * 0.52f, cardBottom, 10f * density, 10f * density, paint)
        canvas.drawRoundRect(w * 0.56f, cardTop, lineRight, cardBottom, 10f * density, 10f * density, paint)
    }
}
