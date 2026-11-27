package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.MemoryAtom
import me.rerere.rikkahub.data.model.MemoryAtomState
import me.rerere.rikkahub.data.model.MemoryRecallCandidate
import me.rerere.rikkahub.data.model.MemoryRecallFilterReason
import kotlin.math.max

object AtomicMemoryRecallEngine {
    private const val DORMANT_WAKE_THRESHOLD = 0.75
    private const val MAX_PER_TOPIC = 2

    fun recall(atoms: List<MemoryAtom>, query: String, tokenBudget: Int): RecallSelection {
        val queryTerms = termsOf(query)
        val ranked = atoms.map { atom ->
            val atomTerms = termsOf(atom.searchableText())
            val overlap = queryTerms.intersect(atomTerms).size
            val lexical = if (queryTerms.isEmpty() || atomTerms.isEmpty()) {
                0.0
            } else {
                (overlap.toDouble() / queryTerms.size * 0.65 + overlap.toDouble() / atomTerms.size * 0.35)
                    .coerceIn(0.0, 1.0)
            }
            val activation = atom.activation.coerceIn(0.0, 1.0)
            val importance = atom.importance?.coerceIn(0.0, 1.0) ?: 0.5
            val stability = atom.stability?.coerceIn(0.0, 1.0) ?: 0.5
            val confidence = atom.confidence?.coerceIn(0.0, 1.0) ?: 0.5
            val feedbackTotal = atom.positiveFeedbackCount + atom.negativeFeedbackCount
            val feedback = if (feedbackTotal == 0) 0.5 else atom.positiveFeedbackCount.toDouble() / feedbackTotal
            val total = lexical * 0.65 + activation * 0.12 + importance * 0.08 +
                stability * 0.05 + confidence * 0.05 + feedback * 0.05 + if (atom.pinned) 0.75 else 0.0
            Scored(atom, total, lexical, activation, importance)
        }.sortedWith(compareByDescending<Scored> { it.total }.thenBy { it.atom.id })

        val wrapperTokens = ContextComposer.estimateTextTokens("<atomic_memory_recall>\n</atomic_memory_recall>")
        var remaining = (tokenBudget - wrapperTokens).coerceAtLeast(0)
        val topics = mutableMapOf<String, Int>()
        val candidates = ranked.mapIndexed { index, scored ->
            val topic = scored.atom.topic?.trim()?.lowercase().orEmpty()
            val line = scored.atom.injectionLine()
            val tokens = ContextComposer.estimateTextTokens(line)
            val reason = when {
                scored.lexical <= 0.0 && !scored.atom.pinned -> MemoryRecallFilterReason.NO_LEXICAL_MATCH
                scored.atom.state == MemoryAtomState.DORMANT && !scored.atom.pinned &&
                    scored.lexical < DORMANT_WAKE_THRESHOLD ->
                    MemoryRecallFilterReason.DORMANT_WEAK_CUE
                topic.isNotEmpty() && topics.getOrDefault(topic, 0) >= MAX_PER_TOPIC ->
                    MemoryRecallFilterReason.DIVERSITY_LIMIT
                tokens > remaining -> MemoryRecallFilterReason.BUDGET_EXCEEDED
                else -> null
            }
            val injected = reason == null
            if (injected) {
                remaining -= tokens
                if (topic.isNotEmpty()) topics[topic] = topics.getOrDefault(topic, 0) + 1
            }
            MemoryRecallCandidate(
                atom = scored.atom,
                rank = index + 1,
                totalScore = scored.total,
                lexicalScore = scored.lexical,
                activationScore = scored.activation,
                importanceScore = scored.importance,
                filteredReason = reason,
                injected = injected,
                tokenCost = if (injected) tokens else 0,
            )
        }
        val selected = candidates.filter { it.injected }
        val content = if (selected.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine("<atomic_memory_recall>")
                selected.forEach { appendLine(it.atom.injectionLine()) }
                append("</atomic_memory_recall>")
            }
        }
        return RecallSelection(
            content = content,
            tokens = if (content.isEmpty()) 0 else ContextComposer.estimateTextTokens(content),
            candidates = candidates,
        )
    }

    private data class Scored(
        val atom: MemoryAtom,
        val total: Double,
        val lexical: Double,
        val activation: Double,
        val importance: Double,
    )

    data class RecallSelection(
        val content: String,
        val tokens: Int,
        val candidates: List<MemoryRecallCandidate>,
    )

    private fun MemoryAtom.searchableText(): String = listOfNotNull(
        statement,
        event,
        change,
        reflection,
        place,
        topic,
        sourceExcerpt,
        people.joinToString(" ").takeIf { it.isNotBlank() },
        tags.joinToString(" ").takeIf { it.isNotBlank() },
    ).joinToString(" ")

    private fun MemoryAtom.injectionLine(): String = buildString {
        append("- [")
        append(id)
        append("] ")
        append(statement.replace(Regex("\\s+"), " ").trim())
        sourceSummary?.let { append(" (source: $it)") }
    }

    internal fun termsOf(text: String): Set<String> {
        val normalized = text.lowercase()
        val terms = Regex("[a-z0-9_]+", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val cjk = normalized.filter { it.code in 0x3400..0x9FFF }
        cjk.forEach { terms += it.toString() }
        cjk.windowed(2).forEach { terms += it }
        return terms
    }
}
