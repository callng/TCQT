package com.owo233.tcqt.features.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactionEmojiCatalogTest {
    @Test
    fun includesBlessBeyondTheDefault165Entries() {
        val codes = IntArray(178) { 0x1f600 + it }.apply { this[177] = 0x3297 }
        val catalog = ReactionEmojiCatalog(codes, emptyMap(), (0 until 165).toList())

        assertEquals(178, catalog.localIds.size)
        assertTrue(177 in catalog.localIds)
        assertEquals(12951, catalog.unicodeByLocalId[177])
        assertEquals(177, catalog.localIdByUnicode[12951])
        assertEquals((0 until 165).toList(), catalog.localIds.take(165))
    }

    @Test
    fun deduplicatesByUnicodeAndKeepsConfiguredOrder() {
        val catalog = ReactionEmojiCatalog(
            intArrayOf(0x1f60a, 0x263a, 0x263a, 0x3297),
            mapOf(0x263a to 1052, 0x1f60a to 2000),
            listOf(2, 0, 2),
        )

        assertEquals(listOf(2, 0, 3), catalog.localIds)
        assertEquals(0x263a, catalog.unicodeByLocalId[1052])
        assertEquals(0x1f60a, catalog.unicodeByLocalId[2000])
    }

    @Test
    fun includesDownloadedMappingsWithoutTreatingFileOffsetsAsUnicode() {
        val catalog = ReactionEmojiCatalog(
            intArrayOf(0x1f60a),
            mapOf(0x1fae0 to 8456, 0x1f680 to 9632),
            listOf(0),
        )

        assertEquals(listOf(0, 8456, 9632), catalog.localIds)
        assertEquals(0x1fae0, catalog.unicodeByLocalId[8456])
        assertEquals(0x1f680, catalog.unicodeByLocalId[9632])
        for (localId in catalog.localIds) {
            val serverId = catalog.unicodeByLocalId.getValue(localId)
            assertEquals(localId, catalog.localIdByUnicode[serverId])
        }
    }

    @Test
    fun excludesInvalidMappingsAtTheHostBoundary() {
        val catalog = ReactionEmojiCatalog(
            intArrayOf(-1, 0, 0xd800, 0x110000, 0x3297),
            mapOf(0x1f680 to -1, 0xdfff to 1000, 0x110000 to 1001),
            listOf(-1, 999, 4),
        )

        assertEquals(listOf(4), catalog.localIds)
        assertFalse(catalog.unicodeByLocalId.containsKey(-1))
        assertEquals(mapOf(4 to 0x3297), catalog.unicodeByLocalId)
    }

    @Test
    fun preservesNativeLocalIdMeaningWhenDownloadedMapsOverlap() {
        val catalog = ReactionEmojiCatalog(intArrayOf(0x3297), mapOf(0x1f680 to 0), emptyList())

        assertEquals(0x3297, catalog.unicodeByLocalId[0])
        assertEquals(listOf(0), catalog.localIds)
    }

    @Test
    fun reversesStaticMappingsBeyondTheNative247EntryReverseMap() {
        val codes = IntArray(822) { 0x1f300 + it }
        val nativeReverseMap = (0 until 247).associate { codes[it] to it }
        val catalog = ReactionEmojiCatalog(codes, nativeReverseMap, (0 until 165).toList())

        assertEquals(822, catalog.localIds.size)
        for ((localId, serverId) in codes.withIndex()) {
            assertEquals(localId, catalog.localIdByUnicode[serverId])
            assertEquals(serverId, catalog.unicodeByLocalId[localId])
        }
    }

    @Test
    fun includesConfiguredUnicodeIdsAndHiddenMappedEntries() {
        val catalog = ReactionEmojiCatalog(
            intArrayOf(0x3297),
            mapOf(0x1fae0 to 8456),
            listOf(0x1f680, 0),
            mapOf(0x1f680 to 0x1f680, 0x1f6f8 to 0x1f6f8, 8456 to 8456, 999 to -1),
        )

        assertEquals(listOf(0x1f680, 0, 8456, 0x1f6f8), catalog.localIds)
        assertEquals(0x1fae0, catalog.unicodeByLocalId[8456])
        assertEquals(0x1f680, catalog.localIdByUnicode[0x1f680])
        assertEquals(0x1f6f8, catalog.localIdByUnicode[0x1f6f8])
        assertFalse(catalog.unicodeByLocalId.containsKey(999))
    }
}
