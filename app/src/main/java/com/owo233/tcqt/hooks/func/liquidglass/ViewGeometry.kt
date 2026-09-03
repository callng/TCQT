package com.owo233.tcqt.hooks.func.liquidglass

import android.view.View
import android.view.ViewParent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 视图几何工具：处理「视图在缩放图层中被绘制」时的坐标换算。
 *
 * 液滴被按住时整个玻璃栏会整体放大，此时玻璃面板与液滴都处于一个被缩放的
 * 绘制图层内部；AGSL 着色器工作在未缩放的本地坐标系中，因此一切对屏幕内容
 * 的采样都必须取得忽略该变换后的位置与缩放系数，否则折射出的背景会被放大
 * 而非露出更多其背后的内容。
 */
internal object ViewGeometry {

    /** 复用的根锚点坐标缓冲；所有调用方均位于 UI 线程。 */
    private val anchorLoc = IntArray(2)

    /**
     * 计算视图在屏幕上的位置，剥离自身及所有祖先的缩放变换。
     *
     * 从视图逐级累加「left + translationX − 父容器 scrollX」的纯布局偏移
     * 直至根视图，再以根视图的 `getLocationOnScreen` 结果为锚点合成。
     * 不能在第一个未缩放的祖先处提前停止：该祖先自身的
     * `getLocationOnScreen` 依然携带更上层施加的缩放。
     */
    fun unscaledScreenPos(view: View, out: IntArray) {
        var x = 0f
        var y = 0f
        var current: View = view
        var parent = current.parent
        while (parent is View) {
            x += current.left + current.translationX - parent.scrollX
            y += current.top + current.translationY - parent.scrollY
            current = parent
            parent = current.parent
        }
        current.getLocationOnScreen(anchorLoc)
        out[0] = (anchorLoc[0] + x).roundToInt()
        out[1] = (anchorLoc[1] + y).roundToInt()
    }

    /**
     * 视图实际被绘制时的累计缩放系数（含全部祖先）。
     *
     * 视图自身的 `scaleX` 并不足够：玻璃栏整体放大时，液滴同时携带
     * 自身的按压缩放与宿主容器的缩放。
     */
    fun cumulativeScale(view: View?): Float {
        var scale = 1f
        var current = view
        while (current != null) {
            scale *= abs(current.scaleX)
            val parent: ViewParent = current.parent
            current = parent as? View
        }
        return if (scale < 0.01f) 1f else scale
    }
}
