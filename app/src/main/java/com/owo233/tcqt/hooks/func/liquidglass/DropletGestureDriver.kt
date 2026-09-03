package com.owo233.tcqt.hooks.func.liquidglass

import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.os.SystemClock
import androidx.core.view.children
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 液滴的按压与拖拽驱动器。
 *
 * 全部动效由五条独立弹簧驱动，各自拥有独立的阻尼与刚度：
 * 位置（临界，以 Tab 为单位而非像素，速度按 Tab 数归一化，不同屏幕密度
 * 手感一致）、速度（欠阻尼，归一化速度映射为拉伸形变）、按压进度
 * （临界）、横向与纵向缩放（欠阻尼）。松手策略是「等液滴接近目标再
 * 回落缩放」，使甩动读起来是一个连贯动作而非「滑动 + 单独缩回」。
 *
 * 点击仍归属宿主：仅在手指横向位移越过触摸阈值后才认领手势。
 */
internal class DropletGestureDriver(
    droplet: View,
    tabRow: ViewGroup?,
    private val density: Float,
) : GlassBarHostLayout.DragHandler {

    private val dropletRef = WeakReference(droplet)
    private var tabRowRef = WeakReference(tabRow)
    private var pillRef = WeakReference<View?>(null)
    private var hostRef = WeakReference<View?>(null)

    private val touchSlop = ViewConfiguration.get(droplet.context).scaledTouchSlop

    private val position = DampedSpring(1.0f, 1000f, VISIBILITY, 0f)
    private val velocity = DampedSpring(0.5f, 300f, VISIBILITY * 10f, 0f)
    private val press = DampedSpring(1.0f, 1000f, 0.001f, 0f)
    private val scaleX = DampedSpring(0.6f, 250f, 0.001f, 1f)
    private val scaleY = DampedSpring(0.7f, 250f, 0.001f, 1f)

    private var downX = 0f
    private var downY = 0f
    private var dragStartValue = 0f
    private var dragging = false
    private var releasePending = false

    /** 帧循环状态。 */
    private var lastFrameNs = 0L
    private var frameScheduled = false

    /** 速度采样：按位置（Tab 单位）在时间上的差分计量。 */
    private var lastSampleMs = 0L
    private var lastSampleValue = 0f

    fun setPill(pill: View?) {
        pillRef = WeakReference(pill)
    }

    /**
     * 整栏缩放的作用对象。
     *
     * 玻璃层、Tab 与液滴必须同步呼吸：仅放大玻璃层会让 Tab
     * 明显脱队，因此缩放施加在宿主容器上。
     */
    fun setHost(host: View?) {
        hostRef = WeakReference(host)
    }

    /**
     * 宿主重建动态 Tab 行后重新绑定。
     *
     * 取消进行中的手势，避免旧几何下的半程弹簧继续作用于新行；
     * 选中观察器会在新行布局完成后吸附到当前 Tab。
     */
    fun setTabRow(tabRow: ViewGroup?) {
        tabRowRef = WeakReference(tabRow)
        dragging = false
        releasePending = false
        lastSampleMs = 0L
        lastFrameNs = 0L
        position.snapTo(position.value.coerceIn(0f, max(tabCount(tabRow) - 1f, 0f)))
        velocity.snapTo(0f)
        press.snapTo(0f)
        scaleX.snapTo(1f)
        scaleY.snapTo(1f)
        apply()
    }

    // ---- 外部驱动 ----

    /**
     * 选中项变化：除手指外唯一允许移动液滴的信号。
     * 拖拽进行中忽略，防止手势中途的页面切换把液滴从指下夺走。
     *
     * @param immediate 首次定位时直接吸附，不播放动画
     */
    fun animateToIndex(index: Int, immediate: Boolean) {
        if (dragging) return
        val tabRow = tabRowRef.get()
        val target = index.toFloat().coerceIn(0f, tabCount(tabRow) - 1f)
        // 松手后的 performClick 会把同一目标回弹回来，跳过以免二次弹跳。
        if (!immediate && abs(position.target - target) < 0.01f) return
        if (immediate) {
            position.snapTo(target)
            velocity.snapTo(0f)
            press.snapTo(0f)
            scaleX.snapTo(1f)
            scaleY.snapTo(1f)
            apply()
            return
        }
        pressOn()
        position.animateTo(target)
        velocity.animateTo(0f)
        releasePending = true
        schedule()
    }

    // ---- 手势 ----

    override fun onIntercept(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                if (isOverDroplet(event)) {
                    pressOn()
                    schedule()
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) return true
                val droplet = dropletRef.get() ?: return false
                if (droplet.visibility != View.VISIBLE) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    dragStartValue = position.target
                    pressOn()
                    schedule()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                release()
                return false
            }
        }
        return false
    }

    override fun onTouch(event: MotionEvent): Boolean {
        val tabRow = tabRowRef.get()
        if (!dragging || tabRow == null) return false
        val count = tabCount(tabRow)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val tabWidth = tabWidth(tabRow)
                if (tabWidth > 0f) {
                    val value = dragStartValue + (event.x - downX) / tabWidth
                    position.animateTo(value.coerceIn(0f, count - 1f))
                    schedule()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 吸附到最近的 Tab 索引，选中状态以宿主为唯一事实来源。
                val index = position.target.coerceIn(0f, count - 1f).roundToInt()
                position.animateTo(index.toFloat())
                velocity.animateTo(0f)
                dragging = false
                release()
                val tab = QQTabLocator.tabAt(tabRow, index)
                if (tab != null && !tab.isSelected) tab.performClick()
                return true
            }
        }
        return true
    }

    /** 命中判定：translationX 是对布局位置的偏移，该位置已含宿主投影内边距。 */
    private fun isOverDroplet(event: MotionEvent): Boolean {
        val droplet = dropletRef.get() ?: return false
        if (droplet.visibility != View.VISIBLE) return false
        val left = droplet.left + droplet.translationX
        val width = droplet.layoutParams?.width?.takeIf { it > 0 } ?: droplet.width
        return event.x >= left && event.x <= left + width
    }

    // ---- 按压与释放 ----

    private fun pressOn() {
        lastSampleMs = 0L
        press.animateTo(1f)
        scaleX.animateTo(PRESSED_SCALE)
        scaleY.animateTo(PRESSED_SCALE)
    }

    private fun release() {
        releasePending = true
        schedule()
    }

    /** 液滴接近目标后才让按压与缩放回落，形成连贯的收尾动作。 */
    private fun maybeFinishRelease() {
        if (!releasePending || dragging) return
        val threshold = max((tabCount(tabRowRef.get()) - 1f) * SETTLE_FRACTION, 0.001f)
        if (abs(position.value - position.target) > threshold) return
        releasePending = false
        press.animateTo(0f)
        scaleX.animateTo(1f)
        scaleY.animateTo(1f)
    }

    // ---- 帧循环 ----

    private fun schedule() {
        if (frameScheduled) return
        frameScheduled = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(::onFrame)
    }

    private fun onFrame(frameNs: Long) {
        frameScheduled = false
        val dt = if (lastFrameNs == 0L) 1f / 60f else (frameNs - lastFrameNs) / 1e9f
        lastFrameNs = frameNs

        var running = position.update(dt)
        sampleVelocity()
        running = velocity.update(dt) || running
        running = press.update(dt) || running
        running = scaleX.update(dt) || running
        running = scaleY.update(dt) || running

        apply()
        maybeFinishRelease()

        // 释放可能重新启动按压与缩放弹簧，须在释放之后复检，
        // 否则循环会提前终止，液滴停留在按住尺寸。
        running = press.isRunning || scaleX.isRunning || scaleY.isRunning ||
            position.isRunning || velocity.isRunning || running

        if (running || releasePending || dragging) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(::onFrame)
        }
    }

    /**
     * 速度采样与归一化：按位置本身差分并除以 Tab 数范围，
     * 使拉伸形变与 Tab 宽度、屏幕密度无关。
     */
    private fun sampleVelocity() {
        val now = SystemClock.uptimeMillis()
        if (lastSampleMs == 0L) {
            lastSampleMs = now
            lastSampleValue = position.value
            return
        }
        val dtMs = now - lastSampleMs
        if (dtMs < 8f) return
        val perSecond = (position.value - lastSampleValue) * 1000f / dtMs
        velocity.animateTo(perSecond / max(1f, tabCount(tabRowRef.get()) - 1f))
        lastSampleMs = now
        lastSampleValue = position.value
    }

    // ---- 应用状态 ----

    private fun apply() {
        val droplet = dropletRef.get()
        val tabRow = tabRowRef.get()
        if (droplet == null || tabRow == null || QQTabLocator.tabCount(tabRow) == 0) return
        val tabWidth = tabWidth(tabRow)
        if (tabWidth <= 0f) return

        // 以布局宽度（LayoutParams）而非 getWidth() 定位：液滴经 LayoutParams
        // 设定尺寸，下次布局前 getWidth() 仍为 0，会把首帧位置错开半个 Tab。
        val first = QQTabLocator.tabAt(tabRow, 0) ?: return
        val dropletWidth = droplet.layoutParams?.width?.takeIf { it > 0 } ?: droplet.width
        val originX = tabRow.left + first.left + (first.width - dropletWidth) * 0.5f
        droplet.translationX = originX + position.value * tabWidth

        // 速度产生的拉伸形变：纵向放大、横向缩小的互补形变模拟液体的惯性。
        val v = velocity.value / 10f
        val along = (v * 0.75f).coerceIn(-STRETCH_LIMIT, STRETCH_LIMIT)
        val across = (v * 0.25f).coerceIn(-STRETCH_LIMIT, STRETCH_LIMIT)
        droplet.scaleX = scaleX.value / (1f - along)
        droplet.scaleY = scaleY.value * (1f - across)

        // 透镜、表面着色与内阴影全部随按压进度淡入。
        val p = press.value
        (droplet as? GlassDropletView)?.let { panel ->
            panel.setProgress(p)
            panel.refresh()
        }

        // 整栏轻量放大，药丸同时承载跟随液滴的交互辉光。
        val pill = pillRef.get()
        if (pill != null && pill.width > 0) {
            val grow = 1f + (PILL_GROWTH_DP * density / pill.width) * p
            val host = hostRef.get()
            val grown = host ?: pill
            grown.scaleX = grow
            grown.scaleY = grow
            if (grown !== pill && pill.scaleX != 1f) {
                pill.scaleX = 1f
                pill.scaleY = 1f
            }
            if (pill is GlassPillView) {
                // 液滴中心在药丸自身坐标系中的位置：两侧都需要计入宿主内边距。
                val centre = droplet.left + droplet.translationX + dropletWidth * 0.5f - pill.left
                pill.setInteraction(p, centre)
            }
        }

        // 真实 Tab 恒保持自然尺寸：拖拽中放大的图标是液滴折射的
        // 放大副本，此处再缩放会与折射效果叠加成两倍。
        for (tab in tabRow.children) {
            if (tab.scaleX != 1f) {
                tab.scaleX = 1f
                tab.scaleY = 1f
            }
        }
    }

    private fun tabWidth(tabRow: ViewGroup?): Float {
        if (tabRow == null || QQTabLocator.tabCount(tabRow) == 0) return 0f
        return QQTabLocator.tabAt(tabRow, 0)?.width?.toFloat() ?: 0f
    }

    private fun tabCount(tabRow: ViewGroup?): Int =
        max(1, QQTabLocator.tabCount(tabRow))

    private companion object {
        const val VISIBILITY = 0.001f

        /** 按住时液滴的放大比例。 */
        const val PRESSED_SCALE = 78f / 56f

        /** 拖拽形变上限。 */
        const val STRETCH_LIMIT = 0.2f

        /** 松手后接近目标行程的比例阈值。 */
        const val SETTLE_FRACTION = 0.025f

        /** 按住时整栏的放大增量（dp）。 */
        const val PILL_GROWTH_DP = 8f
    }
}
