package com.owo233.tcqt.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class TextMatchesTest {
    @Test
    fun `unicode casing preserves original offsets`() {
        assertEquals(listOf(1..1), textMatchRanges("İX", "x"))
        assertEquals(listOf(0..0), textMatchRanges("İ", "i"))
    }

    @Test
    fun `matches are bounded and do not overlap`() {
        assertEquals(listOf(0..1, 2..3), textMatchRanges("aaaaa", "AA"))
        assertEquals(emptyList(), textMatchRanges("abc", " "))
        assertEquals(emptyList(), textMatchRanges("abc", "abcdef"))
    }
}
