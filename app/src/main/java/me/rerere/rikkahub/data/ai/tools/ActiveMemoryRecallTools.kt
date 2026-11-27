package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryRecallFilters
import me.rerere.rikkahub.data.repository.AtomicMemoryRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

private const val ACTIVE_RECALL_DEFAULT_LIMIT = 6
private const val ACTIVE_RECALL_MAX_LIMIT = 12
private const val ACTIVE_RECALL_ATOM_BUDGET = 1_500
private const val ACTIVE_RECALL_MESSAGE_CHARS = 6_000
private const val ACTIVE_RECALL_CONTEXT_CHARS = 2_000

fun buildActiveMemoryRecallTools(
    repository: AtomicMemoryRepository,
    conversationRepository: ConversationRepository,
    memoryId: String,
    assistantId: Uuid,
    currentConversationId: Uuid,
): List<Tool> = listOf(
    Tool(
        name = "memory_recall",
        description = """
            Deliberately search long-term memory and the canonical Conversation Archive.
            Use this when passive memory surfacing is insufficient, when exact wording or provenance matters,
            or when you need older context by keywords, exact phrase, date range, person, topic, or conversation id.
            Results include stable atom/source ids and archive conversation/message ids. context_radius expands around
            each archive hit so you can inspect the surrounding original messages in the same call.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", stringSchema("Keyword query. Optional when another filter is supplied."))
                    put("exact_phrase", stringSchema("Exact phrase that must appear in the atom or archive message."))
                    put("from_date", stringSchema("Inclusive local date in YYYY-MM-DD."))
                    put("to_date", stringSchema("Inclusive local date in YYYY-MM-DD."))
                    put("person", stringSchema("Person/name filter."))
                    put("topic", stringSchema("Topic filter."))
                    put("conversation_id", stringSchema("Restrict archive/source results to one conversation id."))
                    put("context_radius", intSchema("Archive messages before and after each hit (default 2, max 5)."))
                    put("limit", intSchema("Maximum atom and archive results each (default 6, max 12)."))
                }
            )
        },
        execute = { raw ->
            val args = raw.jsonObject
            val query = args.stringOrNull("query")
            val exactPhrase = args.stringOrNull("exact_phrase")
            val person = args.stringOrNull("person")
            val topic = args.stringOrNull("topic")
            val conversationFilter = args.stringOrNull("conversation_id")
            val fromDate = args.dateOrNull("from_date")
            val toDate = args.dateOrNull("to_date")
            require(
                listOf(query, exactPhrase, person, topic, conversationFilter).any { !it.isNullOrBlank() } ||
                    fromDate != null || toDate != null
            ) { "memory_recall needs at least one query or filter" }
            require(fromDate == null || toDate == null || !fromDate.isAfter(toDate)) {
                "from_date must be on or before to_date"
            }

            val targetConversationId = conversationFilter?.let {
                runCatching { Uuid.parse(it) }.getOrElse { error("invalid conversation_id") }
            }
            val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: ACTIVE_RECALL_DEFAULT_LIMIT)
                .coerceIn(1, ACTIVE_RECALL_MAX_LIMIT)
            val radius = (args["context_radius"]?.jsonPrimitive?.intOrNull ?: 2).coerceIn(0, 5)
            val zone = ZoneId.systemDefault()
            val atomQuery = exactPhrase ?: query ?: person ?: topic ?: ""
            val atomResult = repository.activeRecall(
                memoryId = memoryId,
                query = atomQuery,
                tokenBudget = ACTIVE_RECALL_ATOM_BUDGET,
                conversationId = currentConversationId.toString(),
                destination = "ACTIVE_RECALL_TOOL",
                filters = MemoryRecallFilters(
                    exactPhrase = exactPhrase,
                    person = person,
                    topic = topic,
                    occurredFrom = fromDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    occurredTo = toDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1),
                    sourceRef = conversationFilter,
                ),
            )
            val archive = searchArchive(
                conversationRepository = conversationRepository,
                assistantId = assistantId,
                query = query,
                exactPhrase = exactPhrase,
                person = person,
                topic = topic,
                fromDate = fromDate,
                toDate = toDate,
                conversationId = targetConversationId,
                limit = limit,
            )

            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("mode", "ACTIVE_RECALL")
                        put("trace_id", atomResult.traceId)
                        put("atoms", buildJsonArray {
                            atomResult.candidates.filter { it.injected }.take(limit).forEach { candidate ->
                                val atom = candidate.atom
                                add(buildJsonObject {
                                    put("atom_id", atom.id)
                                    put("statement", atom.statement)
                                    atom.occurredAt?.let {
                                        put("occurred_at", Instant.ofEpochMilli(it).toString())
                                    }
                                    put("source", buildJsonObject {
                                        atom.sourceKind?.let { put("kind", it) }
                                        atom.sourceRef?.let { put("ref", it) }
                                        atom.sourceExcerpt?.let { put("excerpt", it) }
                                    })
                                })
                            }
                        })
                        put("archive", buildJsonArray {
                            archive.forEach { hit -> add(hit.toJson(radius)) }
                        })
                    }.toString()
                )
            )
        }
    )
)

private suspend fun searchArchive(
    conversationRepository: ConversationRepository,
    assistantId: Uuid,
    query: String?,
    exactPhrase: String?,
    person: String?,
    topic: String?,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    conversationId: Uuid?,
    limit: Int,
): List<ArchiveHit> {
    val textQuery = exactPhrase ?: query
    val conversations = mutableMapOf<Uuid, Conversation>()
    suspend fun load(id: Uuid): Conversation? =
        conversations[id] ?: conversationRepository.getConversationById(id)?.also { conversations[id] = it }

    val hits = mutableListOf<ArchiveHit>()
    if (!textQuery.isNullOrBlank()) {
        val indexed = conversationRepository.searchMessagesOfAssistants(textQuery, setOf(assistantId))
        for (result in indexed) {
            if (hits.size >= limit) break
            val id = runCatching { Uuid.parse(result.conversationId) }.getOrNull() ?: continue
            if (conversationId != null && id != conversationId) continue
            val conversation = load(id) ?: continue
            val messages = conversation.dialogueMessages()
            val index = messages.indexOfFirst { it.id.toString() == result.messageId }
            if (index < 0) continue
            val message = messages[index]
            if (!message.matchesArchiveFilters(exactPhrase, person, topic, fromDate, toDate)) continue
            hits += ArchiveHit(conversation, messages, index)
        }
        return hits
    }

    val summaries = conversationRepository.getConversationsOfAssistant(assistantId).first()
    for (summary in summaries) {
        if (hits.size >= limit) break
        if (conversationId != null && summary.id != conversationId) continue
        val conversation = load(summary.id) ?: continue
        val messages = conversation.dialogueMessages()
        for ((index, message) in messages.withIndex()) {
            if (hits.size >= limit) break
            if (message.matchesArchiveFilters(exactPhrase, person, topic, fromDate, toDate)) {
                hits += ArchiveHit(conversation, messages, index)
            }
        }
    }
    return hits
}

private fun UIMessage.matchesArchiveFilters(
    exactPhrase: String?,
    person: String?,
    topic: String?,
    fromDate: LocalDate?,
    toDate: LocalDate?,
): Boolean {
    val text = toText()
    if (!exactPhrase.isNullOrBlank() && !text.contains(exactPhrase, ignoreCase = true)) return false
    if (!person.isNullOrBlank() && !text.contains(person, ignoreCase = true)) return false
    if (!topic.isNullOrBlank() && !text.contains(topic, ignoreCase = true)) return false
    val date = LocalDate.parse(createdAt.date.toString())
    if (fromDate != null && date.isBefore(fromDate)) return false
    if (toDate != null && date.isAfter(toDate)) return false
    return true
}

private data class ArchiveHit(
    val conversation: Conversation,
    val messages: List<UIMessage>,
    val index: Int,
) {
    fun toJson(radius: Int): JsonObject {
        val message = messages[index]
        val start = (index - radius).coerceAtLeast(0)
        val endExclusive = (index + radius + 1).coerceAtMost(messages.size)
        return buildJsonObject {
            put("source", buildJsonObject {
                put("kind", "CONVERSATION_ARCHIVE")
                put("conversation_id", conversation.id.toString())
                put("message_id", message.id.toString())
                put("date", message.createdAt.date.toString())
            })
            put("title", conversation.title.ifBlank { "Untitled" })
            put("role", message.role.name)
            put("text", message.toText().take(ACTIVE_RECALL_MESSAGE_CHARS))
            put("context", buildJsonArray {
                messages.subList(start, endExclusive).forEach { nearby ->
                    add(buildJsonObject {
                        put("message_id", nearby.id.toString())
                        put("role", nearby.role.name)
                        put("date", nearby.createdAt.date.toString())
                        put("text", nearby.toText().take(ACTIVE_RECALL_CONTEXT_CHARS))
                    })
                }
            })
        }
    }
}

private fun stringSchema(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun intSchema(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.dateOrNull(key: String): LocalDate? = stringOrNull(key)?.let { value ->
    runCatching { LocalDate.parse(value) }.getOrElse { error("$key must be YYYY-MM-DD") }
}
