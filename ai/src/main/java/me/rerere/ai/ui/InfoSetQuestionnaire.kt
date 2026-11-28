package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

const val INFOSET_QUESTIONNAIRE_KIND = "questionnaire"
const val INFOSET_QUESTIONNAIRE_PHASE_METADATA = "infoset_questionnaire_phase"
const val INFOSET_QUESTIONNAIRE_ID_METADATA = "infoset_questionnaire_id"
const val INFOSET_QUESTIONNAIRE_PAYLOAD_METADATA = "infoset_questionnaire_payload"
const val INFOSET_QUESTIONNAIRE_MEMORY_CANDIDATE_METADATA = "infoset_questionnaire_memory_candidate"

const val INFOSET_QUESTIONNAIRE_PHASE_PROMPT = "prompt"
const val INFOSET_QUESTIONNAIRE_PHASE_ANSWER = "answer"

@Serializable
enum class InfoSetQuestionnaireType {
    @SerialName("single")
    SINGLE,

    @SerialName("multi")
    MULTI,

    @SerialName("ranking")
    RANKING,

    @SerialName("slider")
    SLIDER,

    @SerialName("free_text")
    FREE_TEXT,
}

@Serializable
data class InfoSetQuestionOption(
    val id: String,
    val label: String,
)

@Serializable
data class InfoSetQuestionnaire(
    val id: String,
    val issuerId: String? = null,
    val issuerLabel: String,
    val recipientId: String? = null,
    val recipientLabel: String,
    val type: InfoSetQuestionnaireType,
    val question: String,
    val options: List<InfoSetQuestionOption> = emptyList(),
    val allowOwnAnswer: Boolean = false,
    val sliderMin: Double? = null,
    val sliderMax: Double? = null,
    val sliderStep: Double? = null,
    val createdAt: String,
)

@Serializable
data class InfoSetQuestionnaireAnswer(
    val questionnaireId: String,
    val respondentId: String? = null,
    val respondentLabel: String,
    val questionSnapshot: String,
    val optionLabelsSnapshot: Map<String, String> = emptyMap(),
    val selectedOptionIds: List<String> = emptyList(),
    val rankedOptionIds: List<String> = emptyList(),
    val sliderValue: Double? = null,
    val freeText: String? = null,
    val answeredAt: String,
)

fun createInfoSetQuestionnaire(
    issuerLabel: String,
    recipientLabel: String,
    type: InfoSetQuestionnaireType,
    question: String,
    options: List<InfoSetQuestionOption> = emptyList(),
    issuerId: String? = null,
    recipientId: String? = null,
    allowOwnAnswer: Boolean = false,
    sliderMin: Double? = null,
    sliderMax: Double? = null,
    sliderStep: Double? = null,
    createdAt: Instant = Clock.System.now(),
    id: String = Uuid.random().toString(),
): InfoSetQuestionnaire {
    val isChoice = type == InfoSetQuestionnaireType.SINGLE ||
        type == InfoSetQuestionnaireType.MULTI ||
        type == InfoSetQuestionnaireType.RANKING
    val allowsOwn = type == InfoSetQuestionnaireType.SINGLE ||
        type == InfoSetQuestionnaireType.MULTI
    return InfoSetQuestionnaire(
        id = id,
        issuerId = issuerId,
        issuerLabel = issuerLabel,
        recipientId = recipientId,
        recipientLabel = recipientLabel,
        type = type,
        question = question.trim(),
        options = options.takeIf { isChoice }.orEmpty(),
        allowOwnAnswer = allowOwnAnswer && allowsOwn,
        sliderMin = sliderMin.takeIf { type == InfoSetQuestionnaireType.SLIDER },
        sliderMax = sliderMax.takeIf { type == InfoSetQuestionnaireType.SLIDER },
        sliderStep = sliderStep.takeIf { type == InfoSetQuestionnaireType.SLIDER },
        createdAt = createdAt.toString(),
    ).also { it.validate() }
}

fun createInfoSetQuestionnaireAnswer(
    questionnaire: InfoSetQuestionnaire,
    respondentLabel: String,
    respondentId: String? = null,
    selectedOptionIds: List<String> = emptyList(),
    rankedOptionIds: List<String> = emptyList(),
    sliderValue: Double? = null,
    freeText: String? = null,
    answeredAt: Instant = Clock.System.now(),
): InfoSetQuestionnaireAnswer {
    val normalizedFreeText = freeText?.trim()?.takeIf { it.isNotEmpty() }
    return InfoSetQuestionnaireAnswer(
        questionnaireId = questionnaire.id,
        respondentId = respondentId,
        respondentLabel = respondentLabel,
        questionSnapshot = questionnaire.question,
        optionLabelsSnapshot = questionnaire.options.associate { it.id to it.label },
        selectedOptionIds = when (questionnaire.type) {
            InfoSetQuestionnaireType.SINGLE, InfoSetQuestionnaireType.MULTI -> selectedOptionIds
            else -> emptyList()
        },
        rankedOptionIds = rankedOptionIds.takeIf {
            questionnaire.type == InfoSetQuestionnaireType.RANKING
        }.orEmpty(),
        sliderValue = sliderValue.takeIf { questionnaire.type == InfoSetQuestionnaireType.SLIDER },
        freeText = normalizedFreeText.takeIf {
            questionnaire.type == InfoSetQuestionnaireType.FREE_TEXT ||
                (questionnaire.allowOwnAnswer &&
                    (questionnaire.type == InfoSetQuestionnaireType.SINGLE ||
                        questionnaire.type == InfoSetQuestionnaireType.MULTI))
        },
        answeredAt = answeredAt.toString(),
    ).also { it.validateAgainst(questionnaire) }
}

fun infoSetQuestionnaireNotice(questionnaire: InfoSetQuestionnaire): UIMessagePart.Notice {
    questionnaire.validate()
    return UIMessagePart.Notice(
        title = "选择",
        text = questionnaire.modelFacingText(),
        metadata = questionnaireMetadata(
            questionnaireId = questionnaire.id,
            phase = INFOSET_QUESTIONNAIRE_PHASE_PROMPT,
            payload = Json.encodeToJsonElement(InfoSetQuestionnaire.serializer(), questionnaire),
            memoryCandidate = false,
        ),
    )
}

fun infoSetQuestionnaireAnswerNotice(
    questionnaire: InfoSetQuestionnaire,
    answer: InfoSetQuestionnaireAnswer,
    memoryCandidate: Boolean = true,
): UIMessagePart.Notice {
    questionnaire.validate()
    answer.validateAgainst(questionnaire)
    return UIMessagePart.Notice(
        title = "选择结果",
        text = questionnaire.answerModelFacingText(answer),
        metadata = questionnaireMetadata(
            questionnaireId = questionnaire.id,
            phase = INFOSET_QUESTIONNAIRE_PHASE_ANSWER,
            payload = Json.encodeToJsonElement(InfoSetQuestionnaireAnswer.serializer(), answer),
            memoryCandidate = memoryCandidate,
        ),
    )
}

fun UIMessagePart.Notice.infoSetQuestionnaireOrNull(): InfoSetQuestionnaire? {
    val metadata = metadata ?: return null
    if (metadata.string(INFOSET_KIND_METADATA) != INFOSET_QUESTIONNAIRE_KIND) return null
    if (metadata.string(INFOSET_QUESTIONNAIRE_PHASE_METADATA) != INFOSET_QUESTIONNAIRE_PHASE_PROMPT) return null
    return metadata[INFOSET_QUESTIONNAIRE_PAYLOAD_METADATA]?.decodeQuestionnaire()
}

fun UIMessagePart.Notice.infoSetQuestionnaireAnswerOrNull(): InfoSetQuestionnaireAnswer? {
    val metadata = metadata ?: return null
    if (metadata.string(INFOSET_KIND_METADATA) != INFOSET_QUESTIONNAIRE_KIND) return null
    if (metadata.string(INFOSET_QUESTIONNAIRE_PHASE_METADATA) != INFOSET_QUESTIONNAIRE_PHASE_ANSWER) return null
    return metadata[INFOSET_QUESTIONNAIRE_PAYLOAD_METADATA]?.decodeQuestionnaireAnswer()
}

fun UIMessagePart.Notice.isInfoSetQuestionnaireMemoryCandidate(): Boolean {
    val metadata = metadata ?: return false
    return metadata.string(INFOSET_KIND_METADATA) == INFOSET_QUESTIONNAIRE_KIND &&
        metadata.string(INFOSET_QUESTIONNAIRE_PHASE_METADATA) == INFOSET_QUESTIONNAIRE_PHASE_ANSWER &&
        metadata.boolean(INFOSET_QUESTIONNAIRE_MEMORY_CANDIDATE_METADATA) != false
}

fun UIMessagePart.Notice.infoSetQuestionnaireMemoryCandidateText(): String? {
    if (!isInfoSetQuestionnaireMemoryCandidate()) return null
    val answer = infoSetQuestionnaireAnswerOrNull() ?: return null
    return buildString {
        append("questionnaire_id=")
        append(answer.questionnaireId)
        append("; question=")
        append(answer.questionSnapshot)
        append("; respondent=")
        append(answer.respondentLabel)
        append("; answer=")
        append(answer.summary())
        append("; answered_at=")
        append(answer.answeredAt)
    }
}

private fun questionnaireMetadata(
    questionnaireId: String,
    phase: String,
    payload: JsonElement,
    memoryCandidate: Boolean,
): JsonObject = buildJsonObject {
    put(INFOSET_KIND_METADATA, JsonPrimitive(INFOSET_QUESTIONNAIRE_KIND))
    put(INFOSET_QUESTIONNAIRE_PHASE_METADATA, JsonPrimitive(phase))
    put(INFOSET_QUESTIONNAIRE_ID_METADATA, JsonPrimitive(questionnaireId))
    put(INFOSET_QUESTIONNAIRE_PAYLOAD_METADATA, payload)
    put(INFOSET_QUESTIONNAIRE_MEMORY_CANDIDATE_METADATA, JsonPrimitive(memoryCandidate))
}

private fun InfoSetQuestionnaire.validate() {
    require(id.isNotBlank()) { "questionnaire id must not be blank" }
    require(issuerLabel.isNotBlank()) { "issuerLabel must not be blank" }
    require(recipientLabel.isNotBlank()) { "recipientLabel must not be blank" }
    require(question.isNotBlank()) { "question must not be blank" }
    require(options.map { it.id }.distinct().size == options.size) { "option ids must be unique" }
    require(options.all { it.id.isNotBlank() && it.label.isNotBlank() }) { "options must have ids and labels" }

    when (type) {
        InfoSetQuestionnaireType.SINGLE,
        InfoSetQuestionnaireType.MULTI,
        InfoSetQuestionnaireType.RANKING,
        -> require(options.size >= 2) { "$type requires at least two options" }

        InfoSetQuestionnaireType.SLIDER -> {
            val min = requireNotNull(sliderMin) { "sliderMin is required" }
            val max = requireNotNull(sliderMax) { "sliderMax is required" }
            val step = requireNotNull(sliderStep) { "sliderStep is required" }
            require(min < max) { "sliderMin must be less than sliderMax" }
            require(step > 0.0) { "sliderStep must be positive" }
        }

        InfoSetQuestionnaireType.FREE_TEXT -> Unit
    }
}

private fun InfoSetQuestionnaireAnswer.validateAgainst(questionnaire: InfoSetQuestionnaire) {
    require(questionnaireId == questionnaire.id) { "answer targets a different questionnaire" }
    require(respondentLabel.isNotBlank()) { "respondentLabel must not be blank" }
    require(questionSnapshot == questionnaire.question) { "question snapshot does not match questionnaire" }
    require(optionLabelsSnapshot == questionnaire.options.associate { it.id to it.label }) {
        "option label snapshot does not match questionnaire"
    }
    val optionIds = questionnaire.options.map { it.id }.toSet()

    when (questionnaire.type) {
        InfoSetQuestionnaireType.SINGLE -> {
            val choiceCount = selectedOptionIds.size
            val own = questionnaire.allowOwnAnswer && !freeText.isNullOrBlank()
            require((choiceCount == 1) xor own) { "single answer requires one option or one own answer" }
            require(selectedOptionIds.all { it in optionIds }) { "unknown selected option" }
        }

        InfoSetQuestionnaireType.MULTI -> {
            val own = questionnaire.allowOwnAnswer && !freeText.isNullOrBlank()
            require(selectedOptionIds.isNotEmpty() || own) { "multi answer must not be empty" }
            require(selectedOptionIds.distinct().size == selectedOptionIds.size) { "duplicate selected option" }
            require(selectedOptionIds.all { it in optionIds }) { "unknown selected option" }
        }

        InfoSetQuestionnaireType.RANKING -> {
            require(rankedOptionIds.size == optionIds.size) { "ranking must include every option" }
            require(rankedOptionIds.toSet() == optionIds) { "ranking contains unknown or duplicate options" }
        }

        InfoSetQuestionnaireType.SLIDER -> {
            val value = requireNotNull(sliderValue) { "sliderValue is required" }
            val min = requireNotNull(questionnaire.sliderMin)
            val max = requireNotNull(questionnaire.sliderMax)
            val step = requireNotNull(questionnaire.sliderStep)
            require(value in min..max) { "sliderValue is out of range" }
            val steps = (value - min) / step
            require(abs(steps - round(steps)) < 1e-7) { "sliderValue does not align to sliderStep" }
        }

        InfoSetQuestionnaireType.FREE_TEXT ->
            require(!freeText.isNullOrBlank()) { "free text answer must not be blank" }
    }
}

private fun InfoSetQuestionnaire.modelFacingText(): String = buildString {
    append("[InfoSET questionnaire ")
    append(type.name.lowercase())
    append("] ")
    append(issuerLabel)
    append(" 给 ")
    append(recipientLabel)
    append("：")
    append(question)
    when (type) {
        InfoSetQuestionnaireType.SINGLE,
        InfoSetQuestionnaireType.MULTI,
        InfoSetQuestionnaireType.RANKING,
        -> {
            append("\n选项：")
            options.forEachIndexed { index, option ->
                append("\n")
                append(index + 1)
                append(". ")
                append(option.label)
                append(" [")
                append(option.id)
                append("]")
            }
            if (allowOwnAnswer) append("\n也可以给出自己的答案。")
        }

        InfoSetQuestionnaireType.SLIDER ->
            append("\n范围：$sliderMin–$sliderMax，步长 $sliderStep。")

        InfoSetQuestionnaireType.FREE_TEXT ->
            append("\n请自由填写自己的答案。")
    }
    append("\n若你是此问卷的收件人，请使用 infoset_questionnaire_answer 工具提交结构化回答。")
}

private fun InfoSetQuestionnaire.answerModelFacingText(answer: InfoSetQuestionnaireAnswer): String =
    "[InfoSET questionnaire answer] ${answer.respondentLabel} 对“$question”的回答：${answer.summary()}"

private fun InfoSetQuestionnaireAnswer.summary(): String {
    fun labels(ids: List<String>): String =
        ids.joinToString(" > ") { optionLabelsSnapshot[it] ?: it }

    return when {
        rankedOptionIds.isNotEmpty() -> labels(rankedOptionIds)
        selectedOptionIds.isNotEmpty() && !freeText.isNullOrBlank() ->
            labels(selectedOptionIds) + " + 自定义：" + freeText
        selectedOptionIds.isNotEmpty() -> labels(selectedOptionIds)
        sliderValue != null -> sliderValue.toString()
        !freeText.isNullOrBlank() -> freeText
        else -> "(empty)"
    }
}

private fun JsonElement.decodeQuestionnaire(): InfoSetQuestionnaire? =
    runCatching { Json.decodeFromJsonElement(InfoSetQuestionnaire.serializer(), this) }.getOrNull()

private fun JsonElement.decodeQuestionnaireAnswer(): InfoSetQuestionnaireAnswer? =
    runCatching { Json.decodeFromJsonElement(InfoSetQuestionnaireAnswer.serializer(), this) }.getOrNull()

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
