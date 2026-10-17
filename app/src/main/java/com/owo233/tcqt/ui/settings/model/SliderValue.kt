package com.owo233.tcqt.ui.settings.model

import kotlin.math.roundToLong

/** Align user edits to the declared step, including an uneven final endpoint. */
internal fun normalizeSliderValue(value: Int, min: Int, max: Int, step: Int): Int {
    require(min <= max)
    val clamped = value.coerceIn(min, max)
    if (step <= 1) return clamped
    val snapped =
        (min.toLong() + ((clamped.toLong() - min).toDouble() / step).roundToLong() * step).coerceIn(
            min.toLong(),
            max.toLong(),
        )
    return if (max.toLong() - clamped < kotlin.math.abs(snapped - clamped)) max else snapped.toInt()
}
