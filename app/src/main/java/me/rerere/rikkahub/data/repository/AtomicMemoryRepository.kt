package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.ai.AtomicMemoryRecallEngine
import me.rerere.rikkahub.data.db.dao.MemoryAtomDAO
import me.rerere.rikkahub.data.db.entity.MemoryAtomEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallTraceEntity
import me.rerere.rikkahub.data.model.MemoryAtom
import me.rerere.rikkahub.data.model.MemoryAtomState
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.MemoryRecallFeedback
import me.rerere.rikkahub.data.model.MemoryRecallResult
import java.security.MessageDigest
import kotlin.uuid.Uuid

class AtomicMemoryRepository(
    private val dao: MemoryAtomDAO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAtoms(memoryId: String): Flow<List<MemoryAtom>> =
        dao.observeAtoms(memoryId).map { atoms -> atoms.map(MemoryAtomEntity::toModel) }

    fun observeRecentTraces(memoryId: String) = dao.observeRecentTraces(memoryId)

    suspend fun getAtoms(memoryId: String): List<MemoryAtom> = dao.getAtoms(memoryId).map(MemoryAtomEntity::toModel)

    suspend fun upsert(atom: MemoryAtom) {
        require(atom.statement.isNotBlank()) { "A memory atom needs a statement" }
        dao.upsertAtom(atom.toEntity())
    }

    suspend fun create(memoryId: String, statement: String): MemoryAtom {
        val now = clock()
        return MemoryAtom(
            id = Uuid.random().toString(),
            memoryId = memoryId,
            statement = statement.trim(),
            createdAt = now,
            updatedAt = now,
        ).also { upsert(it) }
    }

    suspend fun delete(id: String) = dao.deleteAtom(id)

    suspend fun deleteAll(memoryId: String) = dao.deleteAtoms(memoryId)

    suspend fun copyNonMarkdown(fromMemoryId: String, toMemoryId: String) {
        val now = clock()
        val copies = getAtoms(fromMemoryId)
            .filter { it.sourceKind != MARKDOWN_SOURCE }
            .map { atom ->
                atom.copy(
                    id = "copy_" + digest("$toMemoryId\u001f${atom.id}").take(32),
                    memoryId = toMemoryId,
                    recallCount = 0,
                    lastRecalledAt = null,
                    positiveFeedbackCount = 0,
                    negativeFeedbackCount = 0,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity()
            }
        if (copies.isNotEmpty()) dao.upsertAtoms(copies)
    }

    suspend fun syncMarkdownFile(memoryId: String, file: MemoryFile, importedAt: Long = clock()): Int {
        val existing = dao.getAtomsBySource(memoryId, MARKDOWN_SOURCE, file.path).associateBy { it.id }
        val atoms = MarkdownMemoryImporter.extract(file).map { statement ->
            val id = stableMarkdownId(memoryId, file.path, statement)
            val old = existing[id]
            MemoryAtomEntity(
                id = id,
                memoryId = memoryId,
                statement = statement,
                importedAt = old?.importedAt ?: importedAt,
                topic = file.title.takeIf { it.isNotBlank() },
                sourceKind = MARKDOWN_SOURCE,
                sourceRef = file.path,
                sourceExcerpt = statement,
                recallCount = old?.recallCount ?: 0,
                lastRecalledAt = old?.lastRecalledAt,
                activation = old?.activation ?: 1.0,
                positiveFeedbackCount = old?.positiveFeedbackCount ?: 0,
                negativeFeedbackCount = old?.negativeFeedbackCount ?: 0,
                state = old?.state ?: MemoryAtomState.ACTIVE.name,
                pinned = old?.pinned ?: (file.path == MemoryFile.PROFILE_PATH || file.path == MemoryFile.PREFERENCES_PATH),
                createdAt = old?.createdAt ?: importedAt,
                updatedAt = if (old?.statement == statement) old.updatedAt else importedAt,
            )
        }
        dao.replaceSource(memoryId, MARKDOWN_SOURCE, file.path, atoms)
        return atoms.size
    }

    suspend fun importMarkdownFiles(memoryId: String, files: List<MemoryFile>, importedAt: Long = clock()): Int {
        var count = 0
        for (file in files) count += syncMarkdownFile(memoryId, file, importedAt)
        return count
    }

    suspend fun deleteMarkdownSource(memoryId: String, path: String) {
        dao.deleteAtomsBySource(memoryId, MARKDOWN_SOURCE, path)
    }

    suspend fun recall(
        memoryId: String,
        query: String,
        tokenBudget: Int,
        conversationId: String? = null,
        destination: String = "OPTIONAL_RECALL",
        traceKey: String? = null,
    ): MemoryRecallResult {
        val now = clock()
        val traceId = traceKey?.let { stableTraceId(memoryId, it) } ?: Uuid.random().toString()
        dao.getTrace(traceId)?.let { trace ->
            return MemoryRecallResult(
                traceId = trace.id,
                memoryId = trace.memoryId,
                query = trace.query,
                createdAt = trace.createdAt,
                tokenBudget = trace.tokenBudget,
                injectedContent = trace.injectedContent,
                injectedTokens = trace.injectedTokens,
                destination = trace.destination,
                candidates = emptyList(),
            )
        }
        val selection = AtomicMemoryRecallEngine.recall(getAtoms(memoryId), query, tokenBudget)
        val result = MemoryRecallResult(
            traceId = traceId,
            memoryId = memoryId,
            query = query,
            createdAt = now,
            tokenBudget = tokenBudget,
            injectedContent = selection.content,
            injectedTokens = selection.tokens,
            destination = destination,
            candidates = selection.candidates,
        )
        dao.recordRecall(
            trace = MemoryRecallTraceEntity(
                id = traceId,
                memoryId = memoryId,
                conversationId = conversationId,
                query = query,
                createdAt = now,
                tokenBudget = tokenBudget,
                injectedContent = selection.content,
                injectedTokens = selection.tokens,
                destination = destination,
            ),
            candidates = selection.candidates.map { candidate ->
                MemoryRecallCandidateEntity(
                    traceId = traceId,
                    atomId = candidate.atom.id,
                    rank = candidate.rank,
                    statement = candidate.atom.statement,
                    sourceSummary = candidate.atom.sourceSummary,
                    totalScore = candidate.totalScore,
                    lexicalScore = candidate.lexicalScore,
                    activationScore = candidate.activationScore,
                    importanceScore = candidate.importanceScore,
                    filteredReason = candidate.filteredReason?.name,
                    injected = candidate.injected,
                    tokenCost = candidate.tokenCost,
                )
            },
            injectedAtomIds = selection.candidates.filter { it.injected }.map { it.atom.id },
        )
        return result
    }

    suspend fun candidates(traceId: String) = dao.getCandidates(traceId)

    suspend fun trace(traceId: String) = dao.getTrace(traceId)

    suspend fun recordFeedback(traceId: String, feedback: MemoryRecallFeedback) {
        dao.recordFeedback(traceId, feedback.name)
    }

    companion object {
        const val MARKDOWN_SOURCE = "MARKDOWN"

        internal fun stableMarkdownId(memoryId: String, path: String, statement: String): String =
            "md_" + digest("$memoryId\u001f$path\u001f${statement.normalizedForIdentity()}").take(32)

        private fun stableTraceId(memoryId: String, traceKey: String): String =
            "recall_" + digest("$memoryId\u001f$traceKey").take(32)

        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

internal object MarkdownMemoryImporter {
    private val bullet = Regex("^\\s*[-*+]\\s+(?:\\[[ xX]]\\s*)?(?:\\[stated\\]\\s*)?(.*)$", RegexOption.IGNORE_CASE)

    fun extract(file: MemoryFile): List<String> {
        val lines = file.displayBody.lines()
        val bullets = lines.mapNotNull { line -> bullet.matchEntire(line)?.groupValues?.get(1)?.cleanStatement() }
        if (bullets.isNotEmpty()) return bullets.distinct()
        return file.displayBody
            .split(Regex("\\n\\s*\\n"))
            .map { paragraph -> paragraph.lines().filterNot { it.trimStart().startsWith("#") }.joinToString(" ") }
            .mapNotNull { it.cleanStatement() }
            .distinct()
    }

    private fun String.cleanStatement(): String? = replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
}

private val atomJson = Json { ignoreUnknownKeys = true }

private fun List<String>.toJsonOrNull(): String? = takeIf { it.isNotEmpty() }?.let(atomJson::encodeToString)

private fun String?.toStringList(): List<String> = if (this.isNullOrBlank()) emptyList() else runCatching {
    atomJson.decodeFromString<List<String>>(this)
}.getOrDefault(emptyList())

private fun MemoryAtom.toEntity() = MemoryAtomEntity(
    id = id,
    memoryId = memoryId,
    statement = statement.trim(),
    occurredAt = occurredAt,
    importedAt = importedAt,
    peopleJson = people.toJsonOrNull(),
    place = place,
    topic = topic,
    event = event,
    change = change,
    reflection = reflection,
    sourceKind = sourceKind,
    sourceRef = sourceRef,
    sourceExcerpt = sourceExcerpt,
    importance = importance,
    stability = stability,
    confidence = confidence,
    tagsJson = tags.toJsonOrNull(),
    relationsJson = relations.toJsonOrNull(),
    recallCount = recallCount,
    lastRecalledAt = lastRecalledAt,
    activation = activation,
    positiveFeedbackCount = positiveFeedbackCount,
    negativeFeedbackCount = negativeFeedbackCount,
    state = state.name,
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun MemoryAtomEntity.toModel() = MemoryAtom(
    id = id,
    memoryId = memoryId,
    statement = statement,
    occurredAt = occurredAt,
    importedAt = importedAt,
    people = peopleJson.toStringList(),
    place = place,
    topic = topic,
    event = event,
    change = change,
    reflection = reflection,
    sourceKind = sourceKind,
    sourceRef = sourceRef,
    sourceExcerpt = sourceExcerpt,
    importance = importance,
    stability = stability,
    confidence = confidence,
    tags = tagsJson.toStringList(),
    relations = relationsJson.toStringList(),
    recallCount = recallCount,
    lastRecalledAt = lastRecalledAt,
    activation = activation,
    positiveFeedbackCount = positiveFeedbackCount,
    negativeFeedbackCount = negativeFeedbackCount,
    state = runCatching { MemoryAtomState.valueOf(state) }.getOrDefault(MemoryAtomState.ACTIVE),
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun String.normalizedForIdentity() = replace(Regex("\\s+"), " ").trim()
