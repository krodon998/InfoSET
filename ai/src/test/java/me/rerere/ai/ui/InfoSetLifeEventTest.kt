package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class InfoSetLifeEventTest {

    @Test
    fun `tap event survives serialization with structured provenance`() {
        val occurredAt = Instant.parse("2026-11-28T03:12:00Z")
        val original = infoSetTapNotice(
            senderLabel = "隐",
            targetLabel = "Aire",
            senderId = "user:self",
            targetId = "assistant:aire",
            style = "轻轻",
            occurredAt = occurredAt,
        )

        val restored = Json.decodeFromString<UIMessagePart>(
            Json.encodeToString<UIMessagePart>(original)
        ) as UIMessagePart.Notice
        val event = restored.infoSetLifeEventOrNull()

        assertNotNull(event)
        assertEquals(INFOSET_LIFE_EVENT_TAP, event?.type)
        assertEquals("user:self", event?.senderId)
        assertEquals("隐", event?.senderLabel)
        assertEquals("assistant:aire", event?.targetId)
        assertEquals("Aire", event?.targetLabel)
        assertEquals("轻轻", event?.style)
        assertEquals(occurredAt.toString(), event?.occurredAt)
        assertTrue(restored.isInfoSetMemoryCandidate())
        assertTrue(restored.text.contains("拍了拍"))
    }

    @Test
    fun `life event may explicitly opt out of memory candidacy`() {
        val notice = infoSetTapNotice(
            senderLabel = "隐",
            targetLabel = "Aire",
            memoryCandidate = false,
            requestBriefResponse = false,
        )

        assertFalse(notice.isInfoSetMemoryCandidate())
        assertEquals(null, notice.infoSetMemoryCandidateText())
    }
}
