package me.rerere.rikkahub.ui.pages.chat

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class ChatTimelineTimeTest {
    @Test
    fun `first message and five minute gaps show a timestamp`() {
        val first = LocalDateTime(2026, 11, 27, 10, 0)
        assertTrue(shouldShowChatTimeSeparator(null, first))
        assertFalse(shouldShowChatTimeSeparator(first, LocalDateTime(2026, 11, 27, 10, 4, 59)))
        assertTrue(shouldShowChatTimeSeparator(first, LocalDateTime(2026, 11, 27, 10, 5)))
    }

    @Test
    fun `timeline labels use compact relative dates`() {
        val today = LocalDate.of(2026, 11, 27)
        assertEquals("10:06", formatChatTimelineTime(LocalDateTime(2026, 11, 27, 10, 6), today))
        assertEquals("昨天 23:12", formatChatTimelineTime(LocalDateTime(2026, 11, 26, 23, 12), today))
        assertEquals("10月3日 08:09", formatChatTimelineTime(LocalDateTime(2026, 10, 3, 8, 9), today))
        assertEquals("2025年12月31日 20:30", formatChatTimelineTime(LocalDateTime(2025, 12, 31, 20, 30), today))
    }
}
