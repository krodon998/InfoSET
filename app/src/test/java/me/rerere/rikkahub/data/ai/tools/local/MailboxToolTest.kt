package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class MailboxToolTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesDateAtShanghaiStartOfDay() {
        val expected = LocalDate.parse("2027-02-14").atStartOfDay(shanghai).toInstant().toEpochMilli()

        assertEquals(expected, parseMailboxUnlockAt("2027-02-14"))
    }

    @Test
    fun parsesLocalDateTimeInShanghai() {
        val expected = LocalDateTime.parse("2027-02-14T20:30:00")
            .atZone(shanghai)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, parseMailboxUnlockAt("2027-02-14T20:30:00"))
    }

    @Test
    fun respectsExplicitOffset() {
        val expected = OffsetDateTime.parse("2027-02-14T20:30:00+09:00").toInstant().toEpochMilli()

        assertEquals(expected, parseMailboxUnlockAt("2027-02-14T20:30:00+09:00"))
    }
}
