package com.owo233.tcqt.features.appearance.liquidglass

import android.view.View
import android.view.ViewParent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 视图几何工具：处理「视图在缩放图层中被绘制」时的坐标换算。
 *
 * 液滴被按住时整栏放大，面板与液滴都在被缩放的绘制图层内，而 AGSL 着色器工作在
 * 未缩放的本地坐标系：采样屏幕内容必须取得忽略该变换后的位置与缩放系数，否则
 * 折射出的背景会被放大而非露出更多其背后的内容。
 */
internal object ViewGeometry {

    /** 复用的根锚点坐标缓冲；所有调用方均位于 UI 线程。 */
    private val anchorLoc = IntArray(2)

    /**
     * 计算视图在屏幕上的位置，剥离自身及所有祖先的缩放变换。
     *
     * 逐级累加「left + translationX − 父容器 scrollX」的纯布局偏移直至根视图，
     * 再以根视图的 `getLocationOnScreen` 结果为锚点合成。不能在第一个未缩放的
     * 祖先处提前停止：该祖先自身的 `getLocationOnScreen` 依然携带更上层施加的缩放。
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
     * 视图左上角在 [ancestor] 自身坐标系中的位置，只累加纯布局偏移——不含缩放，
     * 也不含屏幕原点。
     *
     * 这是宿主子项需要的形态：只读一层 `left`/`top` 仅在两者是父子关系时成立。宿主的
     * 阴影内边距长在宿主上，位于宿主下一层的视图（QQ 的 `QQTabWidget` 自身即 Tab 行）
     * 报告的偏移里已经含了它；逐级累加到宿主再减掉宿主内边距，无论宿主这一版是哪种
     * 树形都只计一次。
     *
     * 屏幕坐标在此处不可用：祖先的缩放（按压缩放施加在宿主上）会污染它，而布局偏移不会。
     *
     * @return [view] 不是 [ancestor] 的后代时返回 false，调用方可据此回退，
     *   而不是信任一个只填了一半的数组。
     */
    fun positionIn(view: View?, ancestor: View?, out: IntArray): Boolean {
        if (view == null || ancestor == null) return false
        var x = 0f
        var y = 0f
        var current: View? = view
        while (current != null && current !== ancestor) {
            x += current.left
            y += current.top
            current = current.parent as? View
        }
        if (current !== ancestor) return false
        out[0] = x.roundToInt()
        out[1] = y.roundToInt()
        return true
    }

    /**
     * 视图实际被绘制时的累计缩放系数（含全部祖先）。
     *
     * 视图自身的 `scaleX` 并不足够：液滴同时携带自身的按压缩放与宿主容器的缩放。
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
