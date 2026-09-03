package com.owo233.tcqt.hooks.func.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.owo233.tcqt.utils.log.Log
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.view.isNotEmpty
import androidx.core.view.isEmpty
import androidx.core.graphics.withClip

/**
 * 跟随选中项的「玻璃液滴」。
 *
 * 静止时是一层轻着色的胶囊；按住/拖拽时随按压进度淡入透镜（折射带与
 * 位移量均乘以进度）。液滴折射的背景是「页面 + 单独以约 1.1 倍绘制的
 * Tab 行副本」的合成——放大且染成选中色的正是这份副本经折射后的样子，
 * 因此液滴必须叠在真实 Tab 之上，而真实 Tab 保持原尺寸，避免二次放大。
 *
 * 未选中 Tab 的副本先整体绘制、再以 SRC_ATOP 覆盖选中色，随后把自带
 * 颜色的子视图（未读角标、红点）按其自身边界重绘一遍以恢复本色。
 */
internal class GlassDropletView(
    context: Context,
    backdrop: ViewGroup,
    tabRow: ViewGroup?,
    private val density: Float,
    dark: Boolean,
) : View(context) {

    private val backdropRef = WeakReference(backdrop)
    private var tabRowRef = WeakReference(tabRow)
    private var pillRef = WeakReference<View?>(null)

    /** 采样外扩量：折射位移量加安全余量。 */
    private val pad = (AMOUNT_DP * density).roundToInt() + (density * 4f).roundToInt()

    private val node = RenderNode("GlassDroplet")
    /** 仅含模糊页面的独立层，清晰 Tab 副本叠加于其上。 */
    private val backdropNode = RenderNode("GlassDropletBackdrop")
    private val backdropEffect: RenderEffect = RenderEffect.createBlurEffect(
        BLUR_DP * density, BLUR_DP * density,
        RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(BACKDROP_SATURATION) })
        ),
        Shader.TileMode.CLAMP,
    )

    private var lensShader: RuntimeShader? = null
    private var innerShadowShader: RuntimeShader? = null

    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressTintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val pillSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 选中色叠层与角标恢复所需画笔。 */
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcAtop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

    // 逐帧复用的缓冲区。
    private val tmpLoc = IntArray(2)
    private val selfLoc = IntArray(2)
    private val srcLoc = IntArray(2)
    private val visibleRect = Rect()

    /** 角标边界池：拖拽期间逐帧执行，避免分配。 */
    private val badgeBounds = ArrayList<RectF>(4)
    private var badgeCount = 0
    private val badgeClipPath = Path()

    private var accentCache = 0

    /** 按压进度 0..1，由手势驱动的弹簧提供。 */
    private var progress = 0f

    private var supported = true

    private var dark = false

    init {
        runCatching {
            lensShader = RuntimeShader(GlassShaderLibrary.DROPLET_LENS)
            innerShadowShader = RuntimeShader(GlassShaderLibrary.INNER_SHADOW)
        }.onFailure {
            supported = false
            Log.e("液滴着色器构建失败，降级为纯着色", it)
        }
        pressTintPaint.color = 0x08000000
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = density
        highlightPaint.color = 0x1FFFFFFF
        innerShadowPaint.style = Paint.Style.FILL
        setTheme(dark)
        setWillNotDraw(false)
    }

    /** 液滴折射的药丸材质提供者。 */
    fun setPill(pill: View?) {
        pillRef = WeakReference(pill)
    }

    /** 底栏重建动态 Tab 行后重新绑定。 */
    fun setTabRow(tabRow: ViewGroup?) {
        tabRowRef = WeakReference(tabRow)
        invalidate()
    }

    /**
     * 主题切换：与药丸共用同一份表面材质参数，
     * 保证拖拽淡入时液滴与药丸颜色无缝衔接。
     */
    fun setTheme(night: Boolean) {
        dark = night
        pillSurfacePaint.color = if (night) 0x662C2C2E else 0x66F2F2F7
        washPaint.color = if (night) 0x1AFFFFFF else 0x1A000000
        invalidate()
    }

    /** 按压进度更新；translationX 由渲染线程移动视图本身，不触发重绘。 */
    fun setProgress(value: Float) {
        if (progress == value) return
        progress = value
        invalidate()
    }

    /**
     * 强制重采样背景：液滴以 translationX 滑动时不会经过重绘，
     * 若不主动刷新，折射内容会停留在按压开始瞬间的画面。
     */
    fun refresh() {
        if (progress > 0.01f) invalidate()
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
        val radius = h * 0.5f
        val p = progress

        // 透镜生效时，表面着色已包含在其背景合成之中（位于清晰 Tab 副本之下）；
        // 降级路径按同样顺序在表面着色后重绘当前 Tab，保持层级一致。
        val drewLens = if (supported && p > 0.01f && canvas.isHardwareAccelerated) {
            runCatching { drawLens(canvas, w, h, radius, p) }
                .onFailure {
                    supported = false
                    Log.e("液滴透镜绘制失败，降级", it)
                }
                .getOrDefault(false)
        } else false

        if (!drewLens) {
            drawSurfaceTints(canvas, 0f, 0f, w.toFloat(), h.toFloat(), radius, p)
            drawRestingTab(canvas)
        }
        if (p > 0f) {
            highlightPaint.alpha = (0x1F * p).roundToInt()
            val half = highlightPaint.strokeWidth * 0.5f
            canvas.drawRoundRect(
                half, half, w - half, h - half,
                radius - half, radius - half, highlightPaint,
            )

            val inner = 8f * density * p
            val innerShader = innerShadowShader
            if (inner > 0.5f && innerShader != null && canvas.isHardwareAccelerated) {
                innerShader.setFloatUniform("size", w.toFloat(), h.toFloat())
                innerShader.setFloatUniform("radius", radius)
                innerShader.setFloatUniform("blur", inner)
                innerShader.setFloatUniform("alpha", 0.15f * p)
                innerShadowPaint.shader = innerShader
                innerShadowPaint.alpha = 255
                canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, innerShadowPaint)
            }
        }
    }

    /** 静置底色与按压加深，绘制在 Tab 内容之下。 */
    private fun drawSurfaceTints(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, p: Float) {
        val restingAlpha = if (dark) 0x1A else 0x0D
        val washAlpha = (restingAlpha * (1f - p)).roundToInt()
        if (washAlpha > 0) {
            washPaint.alpha = washAlpha
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, washPaint)
        }
        val pressAlpha = (0x08 * p).roundToInt()
        if (pressAlpha > 0) {
            pressTintPaint.alpha = pressAlpha
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, pressTintPaint)
        }
    }

    /**
     * 在静置胶囊之上重绘选中的 Tab，使液滴只改变背景而不吞掉
     * 宿主自身的选中图标与文字颜色。
     */
    private fun drawRestingTab(canvas: Canvas) {
        val tabRow = tabRowRef.get() ?: return
        val index = QQTabLocator.selectedIndex(tabRow)
        val tab = QQTabLocator.tabAt(tabRow, index) ?: return
        if (tab.visibility != VISIBLE) return
        ViewGeometry.unscaledScreenPos(this, selfLoc)
        ViewGeometry.unscaledScreenPos(tab, srcLoc)
        canvas.withTranslation(
            (srcLoc[0] - selfLoc[0]).toFloat(),
            (srcLoc[1] - selfLoc[1]).toFloat()
        ) {
            tab.draw(canvas)
        }
    }

    /** 记录液滴尺寸的模糊页面捕获，在任何 Tab 副本叠加之前完成。 */
    private fun recordBlurredBackdrop(nodeWidth: Int, nodeHeight: Int) {
        val backdrop = backdropRef.get() ?: return
        ViewGeometry.unscaledScreenPos(this, selfLoc)
        backdropNode.setPosition(0, 0, nodeWidth, nodeHeight)
        val recording = backdropNode.beginRecording(nodeWidth, nodeHeight)
        try {
            recording.drawColor(if (dark) 0xFF111111.toInt() else 0xFFF7F7F7.toInt())
            var drewAny = false
            for (page in backdrop.children) {
                if (page.visibility != VISIBLE || !page.getGlobalVisibleRect(visibleRect) ||
                    visibleRect.isEmpty
                ) continue
                page.getLocationOnScreen(srcLoc)
                recording.withTranslation(
                    pad - (selfLoc[0] - srcLoc[0]).toFloat(),
                    pad - (selfLoc[1] - srcLoc[1]).toFloat(),
                ) {
                    recording.clipRect(
                        selfLoc[0] - srcLoc[0] - pad,
                        selfLoc[1] - srcLoc[1] - pad,
                        selfLoc[0] - srcLoc[0] - pad + nodeWidth,
                        selfLoc[1] - srcLoc[1] - pad + nodeHeight,
                    )
                    page.draw(recording)
                }
                drewAny = true
            }
            if (!drewAny) {
                backdrop.getLocationOnScreen(srcLoc)
                recording.withTranslation(
                    pad - (selfLoc[0] - srcLoc[0]).toFloat(),
                    pad - (selfLoc[1] - srcLoc[1]).toFloat(),
                ) {
                    backdrop.draw(recording)
                }
            }
        } finally {
            backdropNode.endRecording()
        }
        backdropNode.setRenderEffect(backdropEffect)
    }

    /**
     * 组装透镜输入：已模糊的页面、玻璃表面材质与放大的染色 Tab 副本。
     */
    private fun paintBackdrop(canvas: Canvas, nodeWidth: Int, nodeHeight: Int, p: Float, viewScale: Float) {
        val tabRow = tabRowRef.get()

        // 缩放补偿应用在效果节点的合成阶段而非其录制阶段，
        // 使 4dp 模糊在外层缩放下仍保持 4dp，而非被放大模糊半径。
        canvas.withSave {
            if (abs(viewScale - 1f) > 0.001f) {
                canvas.scale(1f / viewScale, 1f / viewScale, nodeWidth * 0.5f, nodeHeight * 0.5f)
            }
            canvas.drawRenderNode(backdropNode)
        }

        // 玻璃垫属于液滴自身表面，不随页面做逆缩放。
        val radius = (nodeHeight - pad * 2) * 0.5f
        canvas.drawRoundRect(
            pad.toFloat(), pad.toFloat(), (nodeWidth - pad).toFloat(), (nodeHeight - pad).toFloat(),
            radius, radius, pillSurfacePaint,
        )

        // 在原药丸位置上保留其静置材质，模糊页面节点只供给外扩的溢出区域。
        val pill = pillRef.get()
        if (tabRow != null) {
            ViewGeometry.unscaledScreenPos(tabRow, srcLoc)
            val pillSave = canvas.save()
            if (abs(viewScale - 1f) > 0.001f) {
                canvas.scale(1f / viewScale, 1f / viewScale, nodeWidth * 0.5f, nodeHeight * 0.5f)
            }
            canvas.translate(
                pad - (selfLoc[0] - srcLoc[0]).toFloat(),
                pad - (selfLoc[1] - srcLoc[1]).toFloat(),
            )
            if (pill is GlassPillView) {
                ViewGeometry.unscaledScreenPos(pill, tmpLoc)
                canvas.withTranslation(
                    (tmpLoc[0] - srcLoc[0]).toFloat(),
                    (tmpLoc[1] - srcLoc[1]).toFloat()
                ) {
                    pill.drawEmbedded(canvas)
                }
            }
            canvas.restoreToCount(pillSave)
        }

        // 表面着色属于玻璃而非图标文字，置于 Tab 副本之下，
        // 消除按压加深与松手变亮之间的颜色跳变。
        drawSurfaceTints(
            canvas, pad.toFloat(), pad.toFloat(),
            (nodeWidth - pad).toFloat(), (nodeHeight - pad).toFloat(), radius, p,
        )

        if (tabRow != null) {
            // 放大的 Tab 副本：每个 Tab 各自以自身中心缩放，
            // 整行围绕单点缩放会把远端 Tab 推离原位、近端过度放大。
            val scale = 1f + TAB_ZOOM * p
            val accent = accentColour(tabRow)
            val tabSave = canvas.save()
            if (abs(viewScale - 1f) > 0.001f) {
                canvas.scale(1f / viewScale, 1f / viewScale, nodeWidth * 0.5f, nodeHeight * 0.5f)
            }
            canvas.translate(
                pad - (selfLoc[0] - srcLoc[0]).toFloat(),
                pad - (selfLoc[1] - srcLoc[1]).toFloat(),
            )
            for (tab in tabRow.children) {
                if (tab.visibility != VISIBLE) continue
                canvas.withScale(
                    scale, scale,
                    tab.left + tab.width * 0.5f, tab.top + tab.height * 0.5f,
                ) {
                    canvas.translate(tab.left.toFloat(), tab.top.toFloat())
                    drawTintedTab(canvas, tab, accent)
                }
            }
            canvas.restoreToCount(tabSave)
        }
    }

    /**
     * 绘制一份按选中色染色的 Tab 副本。
     *
     * 染色必须是覆盖整个 Tab 自身 `draw()` 的单层叠色：逐叶子染色会
     * 静默丢掉容器自绘的内容（未读气泡正由容器绘制）。自带颜色的
     * 子视图随后按其边界重绘恢复本色——「是否为角标」以
     * `willNotDraw` 判定，宿主的角标在 onDraw 里自绘且无背景。
     */
    private fun drawTintedTab(canvas: Canvas, tab: View, accent: Int) {
        if (tab.visibility != VISIBLE || tab.width <= 0 || tab.height <= 0) return

        if (tab.isSelected) {
            // 选中 Tab 已由宿主自身染成选中色，且其图形并非单色
            // 平涂（蓝色圆盘内嵌白色刻痕），整体平涂叠色会把它压成剪影。
            tab.draw(canvas)
            return
        }

        val w = tab.width
        val h = tab.height
        // 角标悬挂在图标右上角、可越出 Tab 边界，叠色图层需相应外扩。
        val overflow = h * 0.5f
        val layer = canvas.saveLayer(-overflow, -overflow, w + overflow, h + overflow, null)
        tab.draw(canvas)
        accentPaint.color = accent
        accentPaint.xfermode = srcAtop
        canvas.drawRect(-overflow, -overflow, w + overflow, h + overflow, accentPaint)
        accentPaint.xfermode = null
        canvas.restoreToCount(layer)

        if (tab is ViewGroup) {
            badgeCount = 0
            collectBadgeBounds(tab, 0f, 0f, 0)
            if (badgeCount > 0) {
                canvas.withSave {
                    badgeClipPath.reset()
                    for (i in 0 until badgeCount) {
                        val r = badgeBounds[i]
                        val rr = r.height() * 0.5f
                        badgeClipPath.addRoundRect(r, rr, rr, Path.Direction.CW)
                    }
                    canvas.clipPath(badgeClipPath)
                    tab.draw(canvas)
                }
            }
        }
    }

    /**
     * 当前选中的强调色，从处于选中态的标签文字实时读取，
     * 跟随宿主主题而非写死；近白/近黑的无效值被拒绝，
     * 避免选中尚未落定时缓存到未选中颜色。
     */
    private fun accentColour(tabRow: ViewGroup): Int {
        for (tab in tabRow.children) {
            if (!tab.isSelected) continue
            val colour = firstLabelColour(tab, 0)
            if (colour != 0 && !isNeutral(colour)) {
                accentCache = colour
                return colour
            }
        }
        return if (accentCache != 0) accentCache else 0xFF0099FF.toInt()
    }

    /** 近灰判定：RGB 最大与最小通道之差小于阈值即视为无色彩倾向。 */
    private fun isNeutral(colour: Int): Boolean {
        val r = (colour shr 16) and 0xFF
        val g = (colour shr 8) and 0xFF
        val b = colour and 0xFF
        return max(r, max(g, b)) - min(r, min(g, b)) < 24
    }

    /** 深度优先查找第一个无背景、有内容的标签文字颜色。 */
    private fun firstLabelColour(view: View, depth: Int): Int {
        if (depth > 4 || view.visibility != VISIBLE) return 0
        if (view is TextView) {
            return if (view.background == null && !view.text.isNullOrEmpty()) {
                view.currentTextColor or 0xFF000000.toInt()
            } else 0
        }
        if (view is ViewGroup) {
            for (child in view.children) {
                val colour = firstLabelColour(child, depth + 1)
                if (colour != 0) return colour
            }
        }
        return 0
    }

    /**
     * 收集 Tab 内自带颜色（角标、红点）的边界。
     *
     * 宿主的纯图标布局以 translationY 居中角标而非重新布局，
     * 必须按实际绘制位置（left/top + translation）累计，否则
     * 未染色重绘的裁剪区会漏掉下半部分。
     */
    private fun collectBadgeBounds(parent: ViewGroup, offsetX: Float, offsetY: Float, depth: Int) {
        if (depth > 4) return
        for (child in parent.children) {
            if (child.visibility != VISIBLE || child.width <= 0 || child.height <= 0) continue
            val cx = offsetX + child.left + child.translationX
            val cy = offsetY + child.top + child.translationY
            if (child is ViewGroup && child.isNotEmpty()) {
                collectBadgeBounds(child, cx, cy, depth + 1)
            } else if (ownsItsColour(child) && !child.willNotDraw()) {
                addBadge(cx, cy, cx + child.width, cy + child.height)
            }
        }
    }

    /**
     * 判定子视图是否自带颜色：图标与无背景的标签文字随内容色染色，
     * 其余（未读气泡、红点）保持自身样式，避免被选中色平铺吞掉。
     */
    private fun ownsItsColour(view: View): Boolean {
        if (QQTabLocator.isTabIcon(view)) return false
        return !(view is TextView && view.background == null)
    }

    private fun addBadge(left: Float, top: Float, right: Float, bottom: Float) {
        if (badgeCount == badgeBounds.size) badgeBounds.add(RectF())
        badgeBounds[badgeCount++].set(left, top, right, bottom)
    }

    /**
     * Tab 内容栈的半高（以 Tab 中心为基准）。
     *
     * 宿主的 Material TabView 首子项是整列布局框架而非紧凑的
     * 图标+文字栈，无法据此推导，返回负值交由调用方走比例兜底。
     */
    private fun contentHalfHeight(tabRow: ViewGroup?): Float {
        if (tabRow == null || tabRow.isEmpty()) return 0f
        val tab = QQTabLocator.tabAt(tabRow, 0) ?: return 0f
        if (tab.height <= 0) return 0f
        val centre = tab.height * 0.5f
        if (tab !is ViewGroup || tab.isEmpty()) return centre
        val content = tab.getChildAt(0)
        if (content.visibility != VISIBLE || content.height <= 0 ||
            (content.top <= 1 && content.bottom >= tab.height - 1)
        ) return -1f
        return max(centre - content.top, content.top + content.height - centre)
    }

    /** 透镜主绘制：组装背景、配置折射带并绘制。 */
    private fun drawLens(canvas: Canvas, w: Int, h: Int, radius: Float, p: Float): Boolean {
        if (backdropRef.get() == null) return false

        val nodeWidth = w + pad * 2
        val nodeHeight = h + pad * 2
        node.setPosition(0, 0, nodeWidth, nodeHeight)

        // getLocationOnScreen 返回的是缩放后的位置（液滴被按住时放大至 78/56），
        // 而画布处于未缩放本地坐标系，须取无变换坐标。
        ViewGeometry.unscaledScreenPos(this, selfLoc)
        val viewScale = ViewGeometry.cumulativeScale(this)
        recordBlurredBackdrop(nodeWidth, nodeHeight)

        val recording = node.beginRecording(nodeWidth, nodeHeight)
        try {
            paintBackdrop(recording, nodeWidth, nodeHeight, p, viewScale)
        } finally {
            node.endRecording()
        }

        // 折射带宽度：取内容实际留白与固定值中较小者，
        // 保证图标文字不被边缘折射带吞掉，同时边缘形变不显得过薄。
        val lens = lensShader ?: return false
        var band = REFRACTION_DP * density
        val half = contentHalfHeight(tabRowRef.get())
        if (half > 0f) {
            val contentSafe = max(0f, h * 0.5f - half * (1f + TAB_ZOOM * p) / viewScale)
            val preferred = min(band, h * REFRACTION_FRACTION)
            band = min(band, max(contentSafe, preferred))
        } else {
            band = min(band, h * REFRACTION_FRACTION)
        }

        lens.setFloatUniform("size", w.toFloat(), h.toFloat())
        lens.setFloatUniform("offset", -pad.toFloat(), -pad.toFloat())
        lens.setFloatUniform("cornerRadii", radius, radius, radius, radius)
        lens.setFloatUniform("refractionHeight", band * p)
        lens.setFloatUniform("refractionAmount", -band * (AMOUNT_DP / REFRACTION_DP) * p)
        // 深度效果会在如此扁平的液滴中央产生可见环状伪影，故关闭。
        lens.setFloatUniform("depthEffect", 0f)
        lens.setFloatUniform("chromaticAberration", ABERRATION)
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(lens, "content"))

        canvas.withClip(clipPath) {
            canvas.translate(-pad.toFloat(), -pad.toFloat())
            canvas.drawRenderNode(node)
        }
        return true
    }

    private companion object {
        /** 静止零进度、按压满进度下的折射带宽度（dp）。 */
        const val REFRACTION_DP = 10f

        /** 折射位移量（dp）。 */
        const val AMOUNT_DP = 14f

        /** 色散强度。 */
        const val ABERRATION = 0.5f

        /** 折射带相对液滴高度的比例上限。 */
        const val REFRACTION_FRACTION = 0.18f

        /** 与药丸一致的背景模糊与饱和度，保证材质统一。 */
        const val BLUR_DP = 4f
        const val BACKDROP_SATURATION = 1.5f

        /** Tab 副本的放大幅度。 */
        const val TAB_ZOOM = 0.1f
    }
}
