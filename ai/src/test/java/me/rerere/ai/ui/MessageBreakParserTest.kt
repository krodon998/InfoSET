package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class MessageBreakParserTest {
    private val tag = MessageBreakConfig.TAG

    @Test
    fun `normal marker creates two bubbles and no marker creates one`() {
        assertEquals(listOf("第一段", "第二段"), parseMessageBreaks("第一段\n$tag\n第二段").segments)
        assertEquals(listOf("普通回复"), parseMessageBreaks("普通回复").segments)
    }

    @Test
    fun `consecutive and edge markers never create empty bubbles`() {
        val parsed = parseMessageBreaks("$tag\n第一段\n$tag\n$tag\n$tag\n第二段\n$tag")

        assertEquals(listOf("第一段", "第二段"), parsed.segments)
        assertTrue(parsed.segments.none(String::isBlank))
    }

    @Test
    fun `marker split across chunks is withheld then recognized`() {
        val first = "第一段\n<message_br"
        val second = "eak/>\n第二段"
        val tracker = MessageBreakChunkTracker()

        assertFalse(tracker.append(first))
        assertEquals(listOf("第一段"), parseMessageBreaks(first, streaming = true).segments)
        assertTrue(tracker.append(second))
        assertEquals(listOf("第一段", "第二段"), parseMessageBreaks(first + second, streaming = true).segments)
    }

    @Test
    fun `literal marker in fenced code remains visible`() {
        val raw = """
            ```xml
            $tag
            ```
            完成
        """.trimIndent()
        val parsed = parseMessageBreaks(raw)

        assertEquals(listOf(raw), parsed.segments)
        assertEquals(1, parsed.fenceLiteralCount)
    }

    @Test
    fun `escaped literal is unescaped without splitting`() {
        val parsed = parseMessageBreaks("字面量：\\$tag")

        assertEquals(listOf("字面量：$tag"), parsed.segments)
        assertFalse(parsed.fallback)
    }

    @Test
    fun `invalid inline marker falls back to one raw bubble`() {
        val raw = "前文 $tag 后文"
        val parsed = parseMessageBreaks(raw)

        assertTrue(parsed.fallback)
        assertEquals(listOf(raw), parsed.segments)
        assertEquals(MessageBreakWarningCode.INVALID_TAG, parsed.warnings.single().code)
    }

    @Test
    fun `marker interrupting a list is ignored with warning`() {
        val raw = "1. 第一项\n$tag\n2. 第二项"
        val parsed = parseMessageBreaks(raw)

        assertEquals(listOf("1. 第一项\n2. 第二项"), parsed.segments)
        assertEquals(MessageBreakWarningCode.STRUCTURE_INTERRUPTED, parsed.warnings.single().code)
    }

    @Test
    fun `user-authored marker never splits`() {
        val message = UIMessage.user("第一段\n$tag\n第二段")

        assertEquals(
            listOf("第一段\n$tag\n第二段"),
            message.derivedMessageSegments(enabled = true).map(DerivedMessageSegment::text),
        )
    }

    @Test
    fun `derived ids remain stable after recomputation`() {
        val message = UIMessage(
            id = Uuid.parse("00000000-0000-0000-0000-000000000123"),
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("一\n$tag\n二")),
        )

        val first = message.derivedMessageSegments(enabled = true)
        val refreshed = message.copy().derivedMessageSegments(enabled = true)

        assertEquals(listOf("${message.id}:0", "${message.id}:1"), first.map(DerivedMessageSegment::id))
        assertEquals(first, refreshed)
    }

    @Test
    fun `streaming preserves already committed bubble content`() {
        val states = listOf(
            "第一段\n<message_br",
            "第一段\n$tag\n第",
            "第一段\n$tag\n第二段",
        ).map { parseMessageBreaks(it, streaming = true).segments }

        assertEquals("第一段", states[0].first())
        assertEquals("第一段", states[1].first())
        assertEquals("第一段", states[2].first())
        assertEquals("第二段", states[2][1])
    }

    @Test
    fun `segment limit keeps remaining content in final bubble`() {
        val parsed = parseMessageBreaks("一\n$tag\n二\n$tag\n三\n$tag\n四", maxSegments = 3)

        assertEquals(listOf("一", "二", "三\n四"), parsed.segments)
        assertEquals(MessageBreakWarningCode.SEGMENT_LIMIT_EXCEEDED, parsed.warnings.single().code)
    }
}
