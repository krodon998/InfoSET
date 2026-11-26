package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatInputHoldTargetTest {
    private val buttonSize = 38f
    private val gap = 10f

    @Test
    fun `release on send button sends normally`() {
        assertEquals(
            SendHoldTarget.SEND,
            resolveSendHoldTarget(19f, 19f, buttonSize, gap, queueVisible = true),
        )
    }

    @Test
    fun `release on clock queues without requesting api`() {
        assertEquals(
            SendHoldTarget.QUEUE,
            resolveSendHoldTarget(19f, -29f, buttonSize, gap, queueVisible = true),
        )
    }

    @Test
    fun `release between or outside choices does nothing`() {
        assertEquals(
            SendHoldTarget.NONE,
            resolveSendHoldTarget(19f, -5f, buttonSize, gap, queueVisible = true),
        )
        assertEquals(
            SendHoldTarget.NONE,
            resolveSendHoldTarget(60f, -29f, buttonSize, gap, queueVisible = true),
        )
    }

    @Test
    fun `stop button never exposes queue target`() {
        assertEquals(
            SendHoldTarget.NONE,
            resolveSendHoldTarget(19f, -29f, buttonSize, gap, queueVisible = false),
        )
    }

    @Test
    fun `reasoning release maps top and bottom rows`() {
        val choices = 7
        val rowHeight = 42f
        val popupTop = -(rowHeight * choices) - gap

        assertEquals(
            0,
            resolveReasoningHoldIndex(58f, popupTop + 21f, 116f, rowHeight, choices, gap),
        )
        assertEquals(
            choices - 1,
            resolveReasoningHoldIndex(58f, -gap - 21f, 116f, rowHeight, choices, gap),
        )
    }

    @Test
    fun `reasoning release outside choices does nothing`() {
        assertNull(resolveReasoningHoldIndex(140f, -31f, 116f, 42f, 7, gap))
        assertNull(resolveReasoningHoldIndex(58f, -5f, 116f, 42f, 7, gap))
    }
}
