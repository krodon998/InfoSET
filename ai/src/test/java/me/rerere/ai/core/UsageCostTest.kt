package me.rerere.ai.core

import me.rerere.ai.provider.ModelPricing
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.effectivePricing
import me.rerere.ai.ui.estimatedCostMicros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCostTest {
    @Test
    fun `estimates uncached cached write visible and reasoning cost separately`() {
        val usage = TokenUsage(
            promptTokens = 1_000,
            cachedTokens = 400,
            cacheWriteTokens = 100,
            completionTokens = 300,
            reasoningTokens = 200,
        )
        val pricing = ModelPricing(
            inputPerMillion = 2.0,
            cachedInputPerMillion = 0.5,
            cacheWritePerMillion = 3.0,
            outputPerMillion = 10.0,
            reasoningPerMillion = 12.0,
        )

        assertEquals(4_900L, usage.estimatedCostMicros(pricing))
    }

    @Test
    fun `missing base prices leaves cost unknown`() {
        assertNull(TokenUsage(promptTokens = 10).estimatedCostMicros(ModelPricing()))
    }

    @Test
    fun `merge and sum retain detailed token fields`() {
        val merged = TokenUsage(promptTokens = 10, cacheWriteTokens = 2).merge(
            TokenUsage(completionTokens = 3, reasoningTokens = 1)
        )
        assertEquals(TokenUsage(10, 3, 0, 13, 2, 1), merged)

        val summed = merged.sum(TokenUsage(20, 4, 5, 24, 6, 2))
        assertEquals(TokenUsage(30, 7, 5, 37, 8, 3), summed)
    }

    @Test
    fun `gpt 5 6 sol promotional pricing expires instead of silently going stale`() {
        val model = Model(modelId = "openai/gpt-5.6-sol")
        assertEquals(4.0, model.effectivePricing(1_796_111_999_999L).inputPerMillion)
        assertNull(model.effectivePricing(1_796_112_000_000L).inputPerMillion)
    }
}
