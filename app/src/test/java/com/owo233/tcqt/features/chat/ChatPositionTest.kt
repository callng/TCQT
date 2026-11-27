package com.owo233.tcqt.features.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatPositionTest {
    @Test
    fun savedMessageSurvivesInsertedRowsAndLongIdsWithoutLosingPartialScrollOffset() {
        val saved = ChatPosition(Long.MAX_VALUE, 7234567891L, -137)
        val restored = ChatPosition.decode(saved.encode())!!
        assertEquals(saved, restored)
        assertEquals(2, restored.indexIn(listOf(0L to 0L, 90L to 100L, saved.msgId to saved.msgSeq)))
    }

    @Test
    fun sequenceFallbackMustBeUniqueAndExactMessageIdWins() {
        val saved = ChatPosition(99, 12, 0)
        assertEquals(1, saved.indexIn(listOf(8L to 12L, 99L to 12L)))
        assertEquals(0, saved.indexIn(listOf(8L to 12L)))
        assertEquals(-1, saved.indexIn(listOf(8L to 12L, 9L to 12L)))
        assertEquals(-1, saved.indexIn(listOf(8L to 13L)))
    }

    @Test
    fun localNegativeIdCanUsePositiveServerSequence() {
        val saved = ChatPosition(-100, 12, 31)
        assertEquals(saved, ChatPosition.decode(saved.encode()))
        assertEquals(0, saved.indexIn(listOf(-100L to 12L)))
        assertEquals(0, saved.indexIn(listOf(100L to 12L)))
    }

    @Test
    fun onlyOurNavigationCanApplyTheSavedOffset() {
        val saved = ChatPosition(99, 12, -20)
        assertTrue(saved.matchesNavigation(99, 12, 0))
        assertTrue(saved.matchesNavigation(0, 12, 0))
        assertTrue(saved.matchesNavigation(99, 0, 0))
        assertFalse(saved.matchesNavigation(100, 12, 0))
        assertFalse(saved.matchesNavigation(99, 13, 0))
        assertFalse(saved.matchesNavigation(0, 0, 0))
        assertFalse(saved.matchesNavigation(99, 12, 123456))
    }

    @Test
    fun invalidStoredDataIsRejectedAtReadBoundary() {
        for (value in listOf("", "1,2", "1,2,3,4", "a,2,3", "1,2,2147483648", "0,0,0", "-1,0,0")) {
            assertNull(ChatPosition.decode(value), value)
        }
    }

    @Test
    fun accountsAndConversationKindsHaveSeparatePositions() {
        val friend = ChatPosition.conversationKey("10001", 1, "20002")
        assertNotEquals(friend, ChatPosition.conversationKey("10003", 1, "20002"))
        assertNotEquals(friend, ChatPosition.conversationKey("10001", 2, "20002"))
        assertNotEquals(friend, ChatPosition.conversationKey("10001", 1, "20003"))
        assertNull(ChatPosition.conversationKey("", 1, "20002"))
        assertNull(ChatPosition.conversationKey("10001", 1, ""))
        assertNull(ChatPosition.conversationKey("10001", 4, "20002"))
    }
}
