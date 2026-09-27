package com.owo233.tcqt.features.script

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.core.sync.SyncUtils
import kotlin.math.abs

/**
 * 脚本悬浮菜单。
 *
 * 进入聊天界面时出现一个可拖动的小球，点击展开当前运行脚本注册的 `addItem` 菜单；
 * 位置按进程持久化，拖动结束后保存。
 *
 * 这里刻意不使用 Compose：悬浮窗要在宿主 Activity 之上即时挂载与撤销，
 * 用原生 `PopupWindow` + `View` 最省事，也不会和宿主的 View 体系打架。
 */
internal class ScriptMenuView(private val activity: Activity) {

    private var ballWindow: PopupWindow? = null
    private var menuWindow: PopupWindow? = null

    private var lastX = INVALID
    private var lastY = INVALID

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragging = false

    fun show() {
        SyncUtils.runOnUiThread {
            runCatching { showInternal() }
                .onFailure { LogUtils.androidNoFilter.w("脚本悬浮菜单: 显示失败", it) }
        }
    }

    fun dismiss() {
        SyncUtils.runOnUiThread {
            runCatching {
                menuWindow?.dismiss()
                menuWindow = null
                ballWindow?.dismiss()
                ballWindow = null
            }.onFailure { LogUtils.androidNoFilter.w("脚本悬浮菜单: 撤销失败", it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showInternal() {
        if (ballWindow != null) return

        val size = dp(BALL_SIZE_DP)
        val night = HookEnv.isNightMode()

        // 球体完全用代码画：不依赖任何 drawable 资源。
        // 之前用 R.drawable 的 vector 时，资源压缩把它从包里裁掉了
        // （Resources$NotFoundException: File res/xx.xml），而资源 keep 规则不在本模块掌握范围内。
        val ball = View(activity).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (night) BALL_COLOR_NIGHT else BALL_COLOR_LIGHT)
                setStroke(dp(2), if (night) RING_COLOR_NIGHT else RING_COLOR_LIGHT)
            }
            isClickable = true
            isFocusable = true
        }

        // 内容包一层 FrameLayout：PopupWindow 直接以被触摸的 View 作内容时，
        // 触摸序列不会派发到该 View 自身的 OnTouchListener（表现为点了/拖了都没反应）。
        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            addView(ball)
        }

        restorePosition(ball)
        container.setOnTouchListener { _, event -> handleTouch(ball, event) }
        ball.setOnTouchListener { _, event -> handleTouch(ball, event) }

        ballWindow = PopupWindow(container, size, size, false).apply {
            isFocusable = true
            isOutsideTouchable = false
            isTouchable = true
            elevation = 0f
        }

        runCatching {
            ballWindow?.showAtLocation(activity.window.decorView, Gravity.NO_GRAVITY, lastX, lastY)
        }.onFailure {
            LogUtils.androidNoFilter.w("脚本悬浮菜单: 挂载失败", it)
            ballWindow = null
        }
    }

    private fun handleTouch(ball: View, event: MotionEvent): Boolean = when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            touchStartX = event.rawX
            touchStartY = event.rawY
            initialX = lastX
            initialY = lastY
            dragging = false
            true
        }

        MotionEvent.ACTION_MOVE -> {
            val dx = (event.rawX - touchStartX).toInt()
            val dy = (event.rawY - touchStartY).toInt()
            if (abs(dx) > DRAG_THRESHOLD_PX || abs(dy) > DRAG_THRESHOLD_PX) dragging = true
            if (dragging) {
                clampInto(initialX + dx, initialY + dy)
                ballWindow?.update(lastX, lastY, -1, -1)
                menuWindow?.dismiss()
                menuWindow = null
            }
            true
        }

        MotionEvent.ACTION_UP -> {
            if (dragging) {
                persistPosition()
            } else {
                showMenu()
            }
            true
        }

        else -> false
    }

    // ── 菜单 ─────────────────────────────────────────────────────────────

    private fun showMenu() {
        val items = ScriptMenus.buildItems()
        if (items.isEmpty()) return

        val padding = dp(6)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (HookEnv.isNightMode()) 0xF21E1E1E.toInt() else 0xF2FFFFFF.toInt())
            }
        }

        items.forEach { item ->
            when (item) {
                is ScriptMenuItem.Header -> container.addView(headerView(item.scriptName))
                is ScriptMenuItem.Action -> container.addView(actionView(item))
            }
        }

        val window = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = dp(8).toFloat()
        }

        // 菜单贴在球右侧；空间不足时左移，避免出屏
        val metrics = activity.resources.displayMetrics
        val anchorX = (lastX + dp(BALL_SIZE_DP) + dp(4)).coerceAtMost(metrics.widthPixels / 2)
        val anchorY = lastY.coerceAtMost(metrics.heightPixels / 2)

        runCatching {
            window.showAtLocation(activity.window.decorView, Gravity.NO_GRAVITY, anchorX, anchorY)
            menuWindow = window
        }.onFailure { LogUtils.androidNoFilter.w("脚本悬浮菜单: 展开失败", it) }
    }

    private fun headerView(name: String): TextView = TextView(activity).apply {
        text = name
        setTextColor(if (HookEnv.isNightMode()) 0xFF9E9E9E.toInt() else 0xFF888888.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(12), dp(8), dp(12), dp(4))
        isClickable = false
    }

    private fun actionView(item: ScriptMenuItem.Action): TextView = TextView(activity).apply {
        text = item.title
        setTextColor(if (HookEnv.isNightMode()) Color.WHITE else Color.parseColor("#FF222222"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
        setOnClickListener {
            menuWindow?.dismiss()
            menuWindow = null
            ScriptMenus.invoke(item)
        }
    }

    // ── 位置持久化 ───────────────────────────────────────────────────────

    private fun restorePosition(ball: View) {
        val metrics = activity.resources.displayMetrics
        val (savedX, savedY) = ScriptMenuPosition.load()

        if (savedX == INVALID || savedY == INVALID) {
            lastX = metrics.widthPixels - dp(BALL_SIZE_DP) - dp(8)
            lastY = metrics.heightPixels / 2
        } else {
            lastX = savedX
            lastY = savedY
        }
        clampInto(lastX, lastY)

        // 让球在首帧就有正确位置
        ball.x = lastX.toFloat()
        ball.y = lastY.toFloat()
    }

    private fun clampInto(x: Int, y: Int) {
        val metrics = activity.resources.displayMetrics
        val size = dp(BALL_SIZE_DP)
        lastX = x.coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
        lastY = y.coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
    }

    private fun persistPosition() {
        ScriptMenuPosition.save(lastX, lastY)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()

    private companion object {
        const val BALL_SIZE_DP = 40
        const val DRAG_THRESHOLD_PX = 10
        const val INVALID = -1

        /** 球体配色：浅色模式白底灰环，深色模式深底白环。 */
        const val BALL_COLOR_LIGHT = 0xE6FFFFFF.toInt()
        const val BALL_COLOR_NIGHT = 0xE6333333.toInt()
        const val RING_COLOR_LIGHT = 0x33000000
        const val RING_COLOR_NIGHT = 0x66FFFFFF.toInt()
    }
}
