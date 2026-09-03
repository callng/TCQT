package com.owo233.tcqt.hooks.func.liquidglass

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.owo233.tcqt.utils.log.Log
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.graphics.withClip

/**
 * 静置状态的玻璃药丸表面。
 *
 * 渲染管线：以 `RenderNode` 捕获背景页面的绘制指令，先经
 * 「饱和度提升 → 高斯模糊」的 RenderEffect 链，再交给 AGSL 透镜做
 * 圆角矩形边缘折射，最后在其上叠加表面色垫、描边高光与拖拽时的
 * 交互辉光。表面色垫才是可读性的主要来源，模糊刻意保持轻微。
 *
 * 透镜采样范围超出自身边界，捕获区域需向四周各外扩一个折射量；
 * 绘制时再平移回来，使折射带内的采样坐标始终落在有效内容上。
 */
internal class GlassPillView(
    context: Context,
    backdrop: ViewGroup,
    private val density: Float,
    dark: Boolean,
) : View(context) {

    private val backdropRef = WeakReference(backdrop)

    /** 采样外扩量（像素）。 */
    private val pad = (REFRACTION_DP * density).roundToInt()

    private val node = RenderNode("GlassPill")
    /** 液滴复用药丸材质时的独立显示列表，避免与主绘制互相覆盖。 */
    private val embeddedNode = RenderNode("GlassPillEmbedded")

    // 逐帧复用的缓冲区：onDraw 在每次遍历中执行，此处分配会白白增加 GC 压力。
    private val selfLoc = IntArray(2)
    private val srcLoc = IntArray(2)
    private val visibleRect = Rect()

    private var lensShader: RuntimeShader? = null
    private var highlightShader: RuntimeShader? = null
    private var effectChain: RenderEffect? = null
    private var chainWidth = 0
    private var chainHeight = 0

    private val saturateEffect: RenderEffect = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(SATURATION) })
    )

    /** 交互高光的白色辉光画笔与 Plus 混合的白色洗刷画笔。 */
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val washPlusPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 按压进度与液滴中心（本视图坐标系）。 */
    private var interactionProgress = 0f
    private var interactionCentreX = 0f

    private var baseColour = 0
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    /** AGSL 与硬件加速是否可用；任一环节失败即降级为纯色垫。 */
    private var supported = true

    init {
        runCatching {
            lensShader = RuntimeShader(GlassShaderLibrary.PILL_LENS)
            highlightShader = RuntimeShader(GlassShaderLibrary.INTERACTIVE_HIGHLIGHT)
        }.onFailure {
            supported = false
            Log.e("透镜着色器构建失败，降级为纯色垫", it)
        }
        setTheme(dark)
        setWillNotDraw(false)
    }

    fun isSupported() = supported

    /**
     * 表面配色随主题切换。
     *
     * 深色采用深灰表面、微弱白色描边；浅色采用近白表面、稍强的白色描边，
     * 模拟玻璃边缘的镜面反射。
     */
    fun setTheme(dark: Boolean) {
        baseColour = if (dark) 0xFF111111.toInt() else 0xFFF7F7F7.toInt()
        surfacePaint.color = if (dark) 0x662C2C2E else 0x66F2F2F7
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = density
        highlightPaint.color = if (dark) 0x1FFFFFFF else 0x2EFFFFFF
        invalidate()
    }

    /** 更新交互辉光参数：按压进度与液滴中心横坐标。 */
    fun setInteraction(progress: Float, centreX: Float) {
        if (interactionProgress == progress && interactionCentreX == centreX) return
        interactionProgress = progress
        interactionCentreX = centreX
        invalidate()
    }

    /**
     * 对 WRAP_CONTENT 父容器不贡献尺寸。
     *
     * 宿主容器按底栏实际高度确定尺寸；本视图以非 EXACTLY 规格测量时
     * 报告零尺寸，待 FrameLayout 后续以 EXACTLY 规格复测时再取真实值，
     * 否则报出父容器全高会把宿主撑满整屏。
     */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(
            if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthSpec) else 0,
            if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightSpec) else 0,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        clipPath.reset()
        val radius = h * 0.5f
        clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        drawPanel(canvas, w, h, node, ViewGeometry.cumulativeScale(this))
    }

    /** 将静置药丸的材质绘制进液滴的合成背景中。 */
    fun drawEmbedded(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        drawPanel(canvas, w, h, embeddedNode, 1f)
    }

    private fun drawPanel(canvas: Canvas, w: Int, h: Int, renderNode: RenderNode, captureScale: Float) {
        if (supported && canvas.isHardwareAccelerated) {
            runCatching {
                drawGlass(canvas, w, h, renderNode, captureScale)
            }.onFailure {
                supported = false
                Log.e("玻璃绘制失败，降级为纯色垫", it)
            }
        }

        // 表面垫与描边叠在折射背景之上，保证文字可读性。
        val radius = h * 0.5f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, surfacePaint)
        drawInteractiveHighlight(canvas, w, h)
        val half = highlightPaint.strokeWidth * 0.5f
        canvas.drawRoundRect(
            half, half, w - half, h - half,
            radius - half, radius - half, highlightPaint,
        )
    }

    /** 拖拽时跟随液滴的交互辉光：Plus 混合的白色洗刷加径向泛光。 */
    private fun drawInteractiveHighlight(canvas: Canvas, w: Int, h: Int) {
        val progress = interactionProgress
        if (progress <= 0.01f || highlightShader == null) return

        canvas.withClip(clipPath) {
            washPlusPaint.color = 0xFFFFFFFF.toInt()
            washPlusPaint.alpha = (0x0F * progress).roundToInt()
            washPlusPaint.blendMode = BlendMode.PLUS
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), washPlusPaint)

            highlightShader!!.apply {
                setFloatUniform("size", w.toFloat(), h.toFloat())
                setFloatUniform("alpha", 0.12f * progress)
                setFloatUniform("radius", minOf(w, h) * 1.2f)
                setFloatUniform("position", interactionCentreX.coerceIn(0f, w.toFloat()), h * 0.5f)
            }
            bloomPaint.shader = highlightShader
            bloomPaint.blendMode = BlendMode.PLUS
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bloomPaint)
        }
    }

    /**
     * 捕获背景、构建效果链并绘制折射结果。
     *
     * 位置与缩放均取无变换值：拖拽时整栏被放大，按变换后的坐标采样
     * 会得到放大而非展开的背景。效果链中所有 uniform 都是药丸尺寸的
     * 函数，仅在尺寸变化时重建，避免每帧三个原生效果对象的反复分配。
     */
    private fun drawGlass(canvas: Canvas, w: Int, h: Int, renderNode: RenderNode, captureScale: Float) {
        val backdrop = backdropRef.get()
        if (backdrop == null || backdrop.width <= 0) return

        val nodeWidth = w + pad * 2
        val nodeHeight = h + pad * 2
        renderNode.setPosition(0, 0, nodeWidth, nodeHeight)

        ViewGeometry.unscaledScreenPos(this, selfLoc)

        val recording = renderNode.beginRecording(nodeWidth, nodeHeight)
        try {
            // 抵消本视图实际被绘制时的缩放，否则采样背景被拉伸而非露出更多内容。
            if (abs(captureScale - 1f) > 0.001f) {
                recording.scale(1f / captureScale, 1f / captureScale, nodeWidth * 0.5f, nodeHeight * 0.5f)
            }
            // 先铺底色：页面未覆盖到的节点区域若保持透明黑，
            // 经过模糊后会变成实心黑边。
            recording.drawColor(baseColour)
            // 逐页按其屏幕坐标绘制。只画「当前页」会让滑动中的另一半无内容可折射，
            // 整页容器绘制则无法反映页间滑动偏移，故以可见子页各自的实际位置为准。
            var drewAny = false
            for (page in backdrop.children) {
                if (page.visibility != VISIBLE || !page.getGlobalVisibleRect(visibleRect) ||
                    visibleRect.isEmpty
                ) continue
                page.getLocationOnScreen(srcLoc)
                val dx = pad - (selfLoc[0] - srcLoc[0]).toFloat()
                val dy = pad - (selfLoc[1] - srcLoc[1]).toFloat()
                val save = recording.save()
                recording.translate(dx, dy)
                // 平移之后再裁剪，即以页面自身坐标系裁剪，
                // 使 ViewGroup 能提前剔除不相交的子项。
                recording.clipRect(-dx, -dy, -dx + nodeWidth, -dy + nodeHeight)
                page.draw(recording)
                recording.restoreToCount(save)
                drewAny = true
            }
            if (!drewAny) {
                backdrop.getLocationOnScreen(srcLoc)
                recording.translate(
                    pad - (selfLoc[0] - srcLoc[0]).toFloat(),
                    pad - (selfLoc[1] - srcLoc[1]).toFloat(),
                )
                backdrop.draw(recording)
            }
        } finally {
            renderNode.endRecording()
        }

        if (effectChain == null || chainWidth != w || chainHeight != h) {
            lensShader!!.apply {
                setFloatUniform("size", w.toFloat(), h.toFloat())
                setFloatUniform("offset", -pad.toFloat(), -pad.toFloat())
                setFloatUniform("cornerRadii", h * 0.5f, h * 0.5f, h * 0.5f, h * 0.5f)
                setFloatUniform("refractionHeight", REFRACTION_DP * density)
                setFloatUniform("refractionAmount", -REFRACTION_DP * density)
                setFloatUniform("depthEffect", 0f)
            }
            val blur = BLUR_DP * density
            effectChain = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(lensShader!!, "content"),
                RenderEffect.createBlurEffect(blur, blur, saturateEffect, Shader.TileMode.CLAMP),
            )
            chainWidth = w
            chainHeight = h
        }
        renderNode.setRenderEffect(effectChain)

        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.drawRenderNode(renderNode)
        canvas.restoreToCount(save)
    }

    private companion object {
        /** 折射带宽度与折射位移量（dp）。 */
        const val REFRACTION_DP = 24f

        /** 背景模糊半径（dp），轻模糊保证内容仍可辨识。 */
        const val BLUR_DP = 4f

        /** 饱和度提升系数。 */
        const val SATURATION = 1.5f
    }
}
