package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextComposerTest {
    @Test
    fun `keeps block order and caps optional recall`() {
        val composed = ContextComposer.compose(
            coreBond = UIMessage.system("core"),
            currentSharedState = UIMessage.system("state"),
            optionalRecall = UIMessage.system("记".repeat(100)),
            toolContract = UIMessage.system("tools"),
            recentRaw = listOf(UIMessage.user("hi"), UIMessage.assistant("hello")),
            totalTokenBudget = 1_000,
            recallTokenBudget = 20,
            minimumRecentTurns = 1,
        )

        assertEquals(ContextBlockKind.entries, composed.blocks.map { it.kind })
        assertTrue(composed.messages[2].toText().contains("已按 token 预算截断"))
        assertEquals("hi", composed.messages[4].toText())
    }

    @Test
    fun `token budget drops whole old turns and preserves minimum recent turns`() {
        val messages = (1..8).flatMap { turn ->
            listOf(
                UIMessage.user("user-$turn " + "x".repeat(80)),
                UIMessage.assistant("assistant-$turn " + "y".repeat(80)),
            )
        }
        val composed = ContextComposer.compose(
            coreBond = null,
            currentSharedState = null,
            optionalRecall = null,
            toolContract = null,
            recentRaw = messages,
            totalTokenBudget = 100,
            recallTokenBudget = 0,
            minimumRecentTurns = 3,
        )
        val text = composed.messages.joinToString("\n") { it.toText() }

        assertFalse(text.contains("user-1 "))
        assertTrue(text.contains("user-6 "))
        assertTrue(text.contains("assistant-8 "))
        assertEquals(3, composed.messages.count { it.role.name == "USER" })
    }

    @Test
    fun `checkpoint survives token trimming and old raw history stays outside request`() {
        val checkpoint = UIMessage.user("checkpoint").copy(isContextCheckpoint = true)
        val composed = ContextComposer.compose(
            coreBond = null,
            currentSharedState = null,
            optionalRecall = null,
            toolContract = null,
            recentRaw = listOf(UIMessage.user("old"), UIMessage.assistant("old reply"), checkpoint) +
                (1..4).flatMap { listOf(UIMessage.user("u$it"), UIMessage.assistant("a$it")) },
            totalTokenBudget = 20,
            recallTokenBudget = 0,
            minimumRecentTurns = 2,
        )

        assertEquals(checkpoint.id, composed.messages.first().id)
        assertFalse(composed.messages.any { it.toText() == "old" })
        assertTrue(composed.messages.any { it.toText() == "u4" })
    }
}
