package com.owo233.tcqt.features.appearance.liquidglass

import kotlin.test.*

class FloatingBarGeometryTest {
    @Test fun largeScaleFitsViewportIncludingShadow() {
        val geometry = FloatingBarGeometry.fromBase(64, 250, 64, 14, 4, 1.2f, 1000)
        assertTrue(geometry.totalWidth + geometry.shadowPadding * 2 <= 1000)
        assertEquals(geometry.tabWidth * 4 + geometry.horizontalPadding * 2, geometry.totalWidth)
        assertEquals(77, geometry.barHeight)
    }
    @Test fun narrowWindowsKeepAllSlotsAndSymmetricPadding() {
        for (width in 20..400) {
            val geometry = FloatingBarGeometry.fromBase(64, 100, 64, 14, 5, 1.2f, width)
            assertTrue(geometry.tabWidth > 0)
            assertTrue(geometry.totalWidth + geometry.shadowPadding * 2 <= width)
            assertEquals(geometry.tabWidth * 5 + geometry.horizontalPadding * 2, geometry.totalWidth)
        }
    }
    @Test fun defaultBudgetPreservesExistingGeometry() {
        val geometry = FloatingBarGeometry.fromBase(64, 76, 64, 14, 4, 1f)
        assertEquals(312, geometry.totalWidth)
        assertEquals(76, geometry.tabWidth)
        assertEquals(14, geometry.shadowPadding)
    }
}
