package com.owo233.tcqt.ui.settings

import com.owo233.tcqt.ui.settings.model.normalizeSliderValue
import kotlin.test.Test
import kotlin.test.assertEquals

class SliderValueTest {
    @Test
    fun `clamp and snap relative to minimum`() {
        assertEquals(13, normalizeSliderValue(14, 3, 28, 5))
        assertEquals(28, normalizeSliderValue(90, 3, 28, 5))
        assertEquals(3, normalizeSliderValue(-20, 3, 28, 5))
        assertEquals(25, normalizeSliderValue(24, 0, 25, 10))
    }

    @Test
    fun `continuous ranges and large integers remain valid`() {
        assertEquals(6, normalizeSliderValue(6, 0, 10, 1))
        assertEquals(
            Int.MAX_VALUE,
            normalizeSliderValue(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, 10),
        )
    }
}
