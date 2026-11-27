package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.MemoryAtom
import me.rerere.rikkahub.data.model.MemoryAtomState
import me.rerere.rikkahub.data.model.MemoryRecallFilterReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicMemoryRecallEngineTest {
    @Test
    fun `chinese cue ranks matching sparse atom and keeps empty fields valid`() {
        val result = AtomicMemoryRecallEngine.recall(
            atoms = listOf(
                atom("food", "宝宝喜欢黑米粥"),
                atom("code", "InfoSET 使用 Kotlin"),
            ),
            query = "今天想吃黑米粥",
            tokenBudget = 200,
        )

        assertEquals("food", result.candidates.first().atom.id)
        assertTrue(result.candidates.first().injected)
        assertTrue("黑米粥" in result.content)
    }

    @Test
    fun `dormant memory wakes only on a strong cue`() {
        val dormant = atom("dormant", "毕业后不在武汉就业", state = MemoryAtomState.DORMANT)

        val weak = AtomicMemoryRecallEngine.recall(listOf(dormant), "毕业", 200).candidates.single()
        val strong = AtomicMemoryRecallEngine.recall(listOf(dormant), "毕业后不在武汉就业", 200).candidates.single()

        assertEquals(MemoryRecallFilterReason.DORMANT_WEAK_CUE, weak.filteredReason)
        assertTrue(strong.injected)
    }

    @Test
    fun `budget overflow stays visible with a filter reason`() {
        val result = AtomicMemoryRecallEngine.recall(
            atoms = listOf(atom("a", "InfoSET 记忆系统需要稳定 ID"), atom("b", "InfoSET 记忆系统需要完整来源")),
            query = "InfoSET 记忆系统",
            tokenBudget = 1,
        )

        assertTrue(result.candidates.all { it.filteredReason == MemoryRecallFilterReason.BUDGET_EXCEEDED })
        assertEquals("", result.content)
    }

    @Test
    fun `active metadata lookup may select without a lexical cue`() {
        val result = AtomicMemoryRecallEngine.recall(
            atoms = listOf(atom("dated", "A remembered event")),
            query = "",
            tokenBudget = 200,
            allowNonLexicalMatch = true,
        )

        assertTrue(result.candidates.single().injected)
        assertTrue("A remembered event" in result.content)
    }

    @Test
    fun `pinned atom remains stable context without a lexical match`() {
        val pinned = atom("core", "Keep replies concise").copy(pinned = true)

        val candidate = AtomicMemoryRecallEngine.recall(listOf(pinned), "completely unrelated cue", 200)
            .candidates.single()

        assertTrue(candidate.injected)
        assertEquals(null, candidate.filteredReason)
    }

    private fun atom(id: String, statement: String, state: MemoryAtomState = MemoryAtomState.ACTIVE) = MemoryAtom(
        id = id,
        memoryId = "memory",
        statement = statement,
        state = state,
        createdAt = 1,
        updatedAt = 1,
    )
}
