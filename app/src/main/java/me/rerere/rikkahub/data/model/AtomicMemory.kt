package me.rerere.rikkahub.data.model

data class MemoryAtom(
    val id: String,
    val memoryId: String,
    val statement: String,
    val occurredAt: Long? = null,
    val importedAt: Long? = null,
    val people: List<String> = emptyList(),
    val place: String? = null,
    val topic: String? = null,
    val event: String? = null,
    val change: String? = null,
    val reflection: String? = null,
    val sourceKind: String? = null,
    val sourceRef: String? = null,
    val sourceExcerpt: String? = null,
    val importance: Double? = null,
    val stability: Double? = null,
    val confidence: Double? = null,
    val tags: List<String> = emptyList(),
    val relations: List<String> = emptyList(),
    val recallCount: Int = 0,
    val lastRecalledAt: Long? = null,
    val surfaceCount: Int = 0,
    val lastSurfacedAt: Long? = null,
    val activation: Double = 1.0,
    val positiveFeedbackCount: Int = 0,
    val negativeFeedbackCount: Int = 0,
    val state: MemoryAtomState = MemoryAtomState.ACTIVE,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val sourceSummary: String?
        get() = listOfNotNull(sourceKind, sourceRef).takeIf { it.isNotEmpty() }?.joinToString(":")
}

enum class MemoryAtomState {
    ACTIVE,
    COOLING,
    DORMANT,
}

enum class MemoryRecallFilterReason {
    NO_LEXICAL_MATCH,
    DORMANT_WEAK_CUE,
    DIVERSITY_LIMIT,
    BUDGET_EXCEEDED,
}

enum class MemoryAccessMode {
    LEGACY_RECALL,
    PASSIVE_SURFACE,
    ACTIVE_RECALL,
}

enum class MemoryRecallFeedback {
    HELPFUL,
    NOT_HELPFUL,
}

data class MemoryRecallFilters(
    val exactPhrase: String? = null,
    val person: String? = null,
    val topic: String? = null,
    val occurredFrom: Long? = null,
    val occurredTo: Long? = null,
    val sourceRef: String? = null,
)

data class MemoryRecallCandidate(
    val atom: MemoryAtom,
    val rank: Int,
    val totalScore: Double,
    val lexicalScore: Double,
    val activationScore: Double,
    val importanceScore: Double,
    val filteredReason: MemoryRecallFilterReason? = null,
    val injected: Boolean = false,
    val tokenCost: Int = 0,
)

data class MemoryRecallResult(
    val traceId: String,
    val memoryId: String,
    val query: String,
    val createdAt: Long,
    val tokenBudget: Int,
    val injectedContent: String,
    val injectedTokens: Int,
    val destination: String,
    val candidates: List<MemoryRecallCandidate>,
    val mode: MemoryAccessMode = MemoryAccessMode.LEGACY_RECALL,
)
