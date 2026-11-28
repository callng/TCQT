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
        val saved = ChatPosition(Long.MAX_VALUE, 7234567891L, -137, 7234567900L)
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
        for (value in listOf("", "1,2", "1,2,3,4,5", "1,2,3,-1", "1,2,3,a", "a,2,3", "1,2,2147483648", "0,0,0", "-1,0,0")) {
            assertNull(ChatPosition.decode(value), value)
        }
    }

    @Test
    fun oldPositionsRemainReadableWithoutInventingAReadBoundary() {
        assertEquals(ChatPosition(99, 12, -20), ChatPosition.decode("99,12,-20"))
        assertEquals(0L, ChatPosition.unreadCount(0, 100))
    }

    @Test
    fun unreadIncludesMessagesFromBeforeReentryAndUsesTheFurthestReadSequence() {
        // Scrolling back changes the saved anchor but must not move the read boundary back.
        val saved = ChatPosition(99, 100, -20, 110)
        val reentered = ChatPosition.decode(saved.encode())!!
        assertEquals(10L, ChatPosition.unreadCount(reentered.readSeq, 120))
        assertEquals(13L, ChatPosition.unreadCount(reentered.readSeq, 123))
        assertEquals(3L, ChatPosition.unreadCount(120, 123))
        assertEquals(0L, ChatPosition.unreadCount(123, 123))
        assertEquals(0L, ChatPosition.unreadCount(123, 120))
    }

    @Test
    fun lookingBackWithUnreadReturnsToTheReadingViewportAndPreservesItsOffset() {
        val progress = ChatReadingProgress(0)
        val start = ChatPosition(1000, 100, -137)
        progress.onVisible(ChatPosition(1100, 110, 0), start)
        assertNull(progress.returnTarget(400))
        progress.onVisible(ChatPosition(900, 90, 0), ChatPosition(800, 80, -12))
        assertEquals(start, progress.returnTarget(400))
        // Incoming messages change the badge, not the place where reading resumes.
        assertEquals(start, progress.returnTarget(450))
        assertEquals(110L, progress.readSeq)
        assertEquals(340L, ChatPosition.unreadCount(progress.readSeq, 450))
    }

    @Test
    fun returningToTheReadingViewportLetsTheNextClickUseNativeNavigation() {
        val progress = ChatReadingProgress(0)
        val start = ChatPosition(1000, 100, -137)
        progress.onVisible(ChatPosition(1100, 110, 0), start)
        progress.onVisible(ChatPosition(900, 90, 0), null)
        assertEquals(start, progress.returnTarget(400))
        progress.onVisible(ChatPosition(1100, 110, 0), start)
        assertNull(progress.returnTarget(400))
        // Reading further establishes the next return point.
        val next = ChatPosition(1200, 120, -20)
        progress.onVisible(ChatPosition(1300, 130, 0), next)
        progress.onVisible(ChatPosition(1100, 110, 0), null)
        assertEquals(next, progress.returnTarget(400))
    }

    @Test
    fun reentryFarAboveTheReadBoundaryReturnsToThisVisitsEntryViewport() {
        val progress = ChatReadingProgress(990)
        val entry = ChatPosition(1000, 100, -137)
        assertNull(progress.returnTarget(1000))
        progress.onVisible(ChatPosition(1100, 110, 0), entry)
        assertNull(progress.returnTarget(1000))
        progress.onVisible(ChatPosition(900, 90, 0), null)
        assertEquals(entry, progress.returnTarget(1000))
        assertEquals(entry, progress.returnTarget(1010))
        assertEquals(990L, progress.readSeq)
        assertEquals(20L, ChatPosition.unreadCount(progress.readSeq, 1010))
    }

    @Test
    fun readingForwardBelowTheStoredReadBoundaryUpdatesThisVisitsReturnViewport() {
        val progress = ChatReadingProgress(990)
        val entry = ChatPosition(1000, 100, -137)
        progress.onVisible(ChatPosition(1100, 110, 0), entry)
        val next = ChatPosition(1200, 120, -20)
        progress.onVisible(ChatPosition(1300, 130, 0), next)
        progress.onVisible(ChatPosition(1100, 110, 0), entry)
        assertEquals(next, progress.returnTarget(1000))
        assertEquals(990L, progress.readSeq)
    }

    @Test
    fun aCompletedReturnIsConsumedEvenIfReloadedBubbleHeightsChangeTheBottomMessage() {
        val progress = ChatReadingProgress(0)
        val start = ChatPosition(1000, 100, -137)
        progress.onVisible(ChatPosition(1100, 110, 0), start)
        progress.onVisible(ChatPosition(900, 90, 0), null)
        assertEquals(start, progress.returnTarget(400))
        progress.onReturned()
        progress.onVisible(ChatPosition(1090, 109, 0), start)
        assertNull(progress.returnTarget(400))
        assertEquals(110L, progress.readSeq)
    }

    @Test
    fun readingAtTheLatestMessageKeepsTheNativeJumpToBottom() {
        val progress = ChatReadingProgress(0)
        progress.onVisible(ChatPosition(4000, 400, 0), ChatPosition(3900, 390, 0))
        progress.onVisible(ChatPosition(3000, 300, 0), null)
        assertNull(progress.returnTarget(400))
        progress.onVisible(ChatPosition(4500, 450, 0), ChatPosition(4400, 440, 0))
        assertNull(progress.returnTarget(450))
        assertEquals(0L, ChatPosition.unreadCount(progress.readSeq, 450))
    }

    @Test
    fun anUnsentTopMessageDoesNotBecomeAnInvalidSequenceNavigationTarget() {
        val progress = ChatReadingProgress(0)
        val bottom = ChatPosition(1100, 110, 0)
        progress.onVisible(bottom, ChatPosition(-1, 0, -137))
        progress.onVisible(ChatPosition(900, 90, 0), null)
        assertEquals(bottom, progress.returnTarget(400))
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
