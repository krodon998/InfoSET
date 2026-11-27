package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantSettingsPolicyTest {
    @Test
    fun autoContextUsesCurrentExperimentalBaseline() {
        val assistant = Assistant()

        assertNull(assistant.contextTokenBudgetOverride)
        assertEquals(AUTO_CONTEXT_TOKEN_BUDGET, assistant.effectiveContextTokenBudget())
    }

    @Test
    fun legacyContextBudgetStillSurvivesWhenNoNewOverrideExists() {
        val assistant = Assistant(contextTokenBudget = 36_000)

        assertEquals(36_000, assistant.effectiveContextTokenBudget())
    }

    @Test
    fun advancedContextOverrideIsRestrictedToExperimentPresets() {
        for (budget in SUPPORTED_CONTEXT_TOKEN_BUDGET_OVERRIDES) {
            assertEquals(
                budget,
                Assistant().withContextTokenBudgetOverride(budget).effectiveContextTokenBudget(),
            )
        }
        assertEquals(
            AUTO_CONTEXT_TOKEN_BUDGET,
            Assistant().withContextTokenBudgetOverride(null).effectiveContextTokenBudget(),
        )
    }

    @Test
    fun providerModelOutputLimitDefaultsToAuto() {
        assertNull(Model().maxOutputTokens)
        assertEquals(12_000, Model(maxOutputTokens = 12_000).maxOutputTokens)
    }
}
