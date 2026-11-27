package me.rerere.ai.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Instant

const val INFOSET_LIFE_EVENT_KIND = "life_event"
const val INFOSET_LIFE_EVENT_TYPE_METADATA = "infoset_life_event_type"
const val INFOSET_LIFE_EVENT_SENDER_ID_METADATA = "infoset_life_event_sender_id"
const val INFOSET_LIFE_EVENT_SENDER_LABEL_METADATA = "infoset_life_event_sender_label"
const val INFOSET_LIFE_EVENT_TARGET_ID_METADATA = "infoset_life_event_target_id"
const val INFOSET_LIFE_EVENT_TARGET_LABEL_METADATA = "infoset_life_event_target_label"
const val INFOSET_LIFE_EVENT_STYLE_METADATA = "infoset_life_event_style"
const val INFOSET_LIFE_EVENT_OCCURRED_AT_METADATA = "infoset_life_event_occurred_at"
const val INFOSET_LIFE_EVENT_MEMORY_CANDIDATE_METADATA = "infoset_life_event_memory_candidate"
const val INFOSET_LIFE_EVENT_RESPONSE_HINT_METADATA = "infoset_life_event_response_hint"

const val INFOSET_LIFE_EVENT_TAP = "tap"
const val INFOSET_LIFE_EVENT_RESPONSE_BRIEF = "brief"

data class InfoSetLifeEvent(
    val type: String,
    val senderId: String? = null,
    val senderLabel: String,
    val targetId: String? = null,
    val targetLabel: String,
    val style: String? = null,
    val occurredAt: String,
    val memoryCandidate: Boolean = true,
    val responseHint: String? = null,
)

fun infoSetLifeEventNotice(event: InfoSetLifeEvent): UIMessagePart.Notice =
    UIMessagePart.Notice(
        title = when (event.type) {
            INFOSET_LIFE_EVENT_TAP -> "拍一拍"
            else -> "生活事件"
        },
        text = event.modelFacingText(),
        metadata = event.toMetadata(),
    )

fun infoSetTapNotice(
    senderLabel: String,
    targetLabel: String,
    senderId: String? = null,
    targetId: String? = null,
    style: String? = "轻轻",
    occurredAt: Instant = Clock.System.now(),
    memoryCandidate: Boolean = true,
    requestBriefResponse: Boolean = true,
): UIMessagePart.Notice = infoSetLifeEventNotice(
    InfoSetLifeEvent(
        type = INFOSET_LIFE_EVENT_TAP,
        senderId = senderId,
        senderLabel = senderLabel,
        targetId = targetId,
        targetLabel = targetLabel,
        style = style,
        occurredAt = occurredAt.toString(),
        memoryCandidate = memoryCandidate,
        responseHint = INFOSET_LIFE_EVENT_RESPONSE_BRIEF.takeIf { requestBriefResponse },
    )
)

fun UIMessagePart.Notice.infoSetLifeEventOrNull(): InfoSetLifeEvent? {
    val metadata = metadata ?: return null
    if (metadata.string(INFOSET_KIND_METADATA) != INFOSET_LIFE_EVENT_KIND) return null

    val type = metadata.string(INFOSET_LIFE_EVENT_TYPE_METADATA) ?: return null
    val senderLabel = metadata.string(INFOSET_LIFE_EVENT_SENDER_LABEL_METADATA) ?: return null
    val targetLabel = metadata.string(INFOSET_LIFE_EVENT_TARGET_LABEL_METADATA) ?: return null
    val occurredAt = metadata.string(INFOSET_LIFE_EVENT_OCCURRED_AT_METADATA) ?: return null

    return InfoSetLifeEvent(
        type = type,
        senderId = metadata.string(INFOSET_LIFE_EVENT_SENDER_ID_METADATA),
        senderLabel = senderLabel,
        targetId = metadata.string(INFOSET_LIFE_EVENT_TARGET_ID_METADATA),
        targetLabel = targetLabel,
        style = metadata.string(INFOSET_LIFE_EVENT_STYLE_METADATA),
        occurredAt = occurredAt,
        memoryCandidate = metadata.string(INFOSET_LIFE_EVENT_MEMORY_CANDIDATE_METADATA) != "false",
        responseHint = metadata.string(INFOSET_LIFE_EVENT_RESPONSE_HINT_METADATA),
    )
}

fun UIMessagePart.Notice.isInfoSetMemoryCandidate(): Boolean =
    infoSetLifeEventOrNull()?.memoryCandidate == true

fun UIMessagePart.Notice.infoSetMemoryCandidateText(): String? =
    infoSetLifeEventOrNull()
        ?.takeIf { it.memoryCandidate }
        ?.memoryCandidateText()

private fun InfoSetLifeEvent.toMetadata(): JsonObject = buildJsonObject {
    put(INFOSET_KIND_METADATA, JsonPrimitive(INFOSET_LIFE_EVENT_KIND))
    put(INFOSET_LIFE_EVENT_TYPE_METADATA, JsonPrimitive(type))
    senderId?.let { put(INFOSET_LIFE_EVENT_SENDER_ID_METADATA, JsonPrimitive(it)) }
    put(INFOSET_LIFE_EVENT_SENDER_LABEL_METADATA, JsonPrimitive(senderLabel))
    targetId?.let { put(INFOSET_LIFE_EVENT_TARGET_ID_METADATA, JsonPrimitive(it)) }
    put(INFOSET_LIFE_EVENT_TARGET_LABEL_METADATA, JsonPrimitive(targetLabel))
    style?.let { put(INFOSET_LIFE_EVENT_STYLE_METADATA, JsonPrimitive(it)) }
    put(INFOSET_LIFE_EVENT_OCCURRED_AT_METADATA, JsonPrimitive(occurredAt))
    put(INFOSET_LIFE_EVENT_MEMORY_CANDIDATE_METADATA, JsonPrimitive(memoryCandidate))
    responseHint?.let { put(INFOSET_LIFE_EVENT_RESPONSE_HINT_METADATA, JsonPrimitive(it)) }
}

private fun InfoSetLifeEvent.modelFacingText(): String = when (type) {
    INFOSET_LIFE_EVENT_TAP -> buildString {
        append("[InfoSET life event: tap] ")
        append(senderLabel)
        append(' ')
        style?.takeIf { it.isNotBlank() }?.let {
            append(it)
            append(' ')
        }
        append("拍了拍 ")
        append(targetLabel)
        append("。")
        if (responseHint == INFOSET_LIFE_EVENT_RESPONSE_BRIEF) {
            append(" 若回应，请简短、自然地回应这个动作。")
        }
    }

    else -> "[InfoSET life event: $type] $senderLabel -> $targetLabel"
}

private fun InfoSetLifeEvent.memoryCandidateText(): String = buildString {
    append("type=")
    append(type)
    append("; sender=")
    append(senderLabel)
    senderId?.let {
        append(" (")
        append(it)
        append(')')
    }
    append("; target=")
    append(targetLabel)
    targetId?.let {
        append(" (")
        append(it)
        append(')')
    }
    style?.takeIf { it.isNotBlank() }?.let {
        append("; style=")
        append(it)
    }
    append("; occurred_at=")
    append(occurredAt)
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull
