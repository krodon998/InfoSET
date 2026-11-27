package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.MessageBreakConfig
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBreakContractTransformerTest {
    @Test
    fun `contract is injected into system prompt without changing user content`() {
        val user = UIMessage.user("原始正文")

        val result = injectMessageBreakContract(listOf(UIMessage.system("身份"), user), maxSegments = 3)

        assertTrue(result.first().toText().contains(MessageBreakConfig.TAG))
        assertTrue(result.first().toText().contains("at most 3 segments"))
        assertEquals("原始正文", result.last().toText())
    }
}
