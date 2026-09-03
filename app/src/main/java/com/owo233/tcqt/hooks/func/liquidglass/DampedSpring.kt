package com.owo233.tcqt.hooks.func.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 单自由度临界参数化弹簧，参数语义与 Compose 的
 * `spring(dampingRatio, stiffness, visibilityThreshold)` 一致。
 *
 * 采用半隐式欧拉法积分：先按当前加速度更新速度，再用新速度更新位置，
 * 在高达 1000 的刚度下仍保持数值稳定。为防止掉帧导致积分器发散，
 * 单次更新被切分为不超过 1/240 秒的子步，且总步长被钳制在 64ms 以内。
 *
 * @property dampingRatio 阻尼比，1.0 为临界阻尼（无过冲）
 * @property stiffness 刚度，越大回复越快
 * @property threshold 收敛阈值，位置与速度同时低于该值时视为静止
 */
internal class DampedSpring(
    private val dampingRatio: Float,
    private val stiffness: Float,
    private val threshold: Float,
    initial: Float,
) {
    var value = initial
        private set
    var target = initial
        private set
    var velocity = 0f
        private set
    var isRunning = false
        private set

    /** 设置新的目标值并开始动画；目标未变化时不重复启动。 */
    fun animateTo(newTarget: Float) {
        if (target == newTarget) return
        target = newTarget
        isRunning = true
    }

    /** 立即跳转到指定值，清除速度并停止动画。 */
    fun snapTo(newValue: Float) {
        value = newValue
        target = newValue
        velocity = 0f
        isRunning = false
    }

    /**
     * 推进 [dtSeconds] 秒。
     *
     * @return 仍在运动中返回 true；位置与速度均已收敛到阈值内则吸附到目标并返回 false
     */
    fun update(dtSeconds: Float): Boolean {
        if (!isRunning) return false

        var remaining = dtSeconds.coerceAtMost(MAX_STEP_SECONDS)
        val damping = 2f * dampingRatio * sqrt(stiffness)
        while (remaining > 0f) {
            val step = minOf(remaining, SUB_STEP_SECONDS)
            remaining -= step
            val accel = -stiffness * (value - target) - damping * velocity
            velocity += accel * step
            value += velocity * step
        }

        if (abs(value - target) < threshold && abs(velocity) < threshold * 10f) {
            value = target
            velocity = 0f
            isRunning = false
        }
        return isRunning
    }

    private companion object {
        const val SUB_STEP_SECONDS = 1f / 240f
        const val MAX_STEP_SECONDS = 0.064f
    }
}
