package me.rerere.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplyExcerptTest {
    @Test
    fun `119 120 and 121 character sources use the configured 25 grapheme budget`() {
        listOf(119, 120, 121).forEach { length ->
            val excerpt = "字".repeat(length).asReplyReferenceExcerpt()

            assertEquals("字".repeat(24) + "…", excerpt)
            assertEquals(25, excerpt.codePointCount(0, excerpt.length))
        }
    }

    @Test
    fun `emoji on truncation boundary is never split`() {
        val excerpt = "字".repeat(23) + "😀" + "尾巴"

        assertEquals("字".repeat(23) + "😀…", excerpt.asReplyReferenceExcerpt())
        assertFalse(excerpt.asReplyReferenceExcerpt().contains('\uFFFD'))
    }
}
