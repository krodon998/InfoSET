package me.rerere.rikkahub.ui.components.message.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MailboxToolUITest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun futureLetterIsLocked() {
        val arguments = buildJsonObject {
            put("unlock_at", "2027-02-14T20:30:00")
            put("title", "hidden")
            put("body", "hidden")
        }
        val beforeDelivery = LocalDateTime.parse("2027-02-14T20:29:59")
            .atZone(shanghai)
            .toInstant()
            .toEpochMilli()

        assertTrue(isMailboxToolLocked(arguments, beforeDelivery))
    }

    @Test
    fun deliveredLetterCanUseNormalPreview() {
        val arguments = buildJsonObject {
            put("unlock_at", "2027-02-14T20:30:00")
        }
        val afterDelivery = LocalDateTime.parse("2027-02-14T20:30:01")
            .atZone(shanghai)
            .toInstant()
            .toEpochMilli()

        assertFalse(isMailboxToolLocked(arguments, afterDelivery))
    }

    @Test
    fun missingOrMalformedTimeFailsClosed() {
        assertTrue(isMailboxToolLocked(buildJsonObject { }, Long.MAX_VALUE))
        assertTrue(
            isMailboxToolLocked(
                buildJsonObject { put("unlock_at", "not-a-time") },
                Long.MAX_VALUE,
            )
        )
    }
}
