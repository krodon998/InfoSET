package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.InfoSetQuestionnaire
import me.rerere.ai.ui.InfoSetQuestionnaireType
import me.rerere.ai.ui.InfoSetQuestionOption
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.createInfoSetQuestionnaire
import me.rerere.ai.ui.createInfoSetQuestionnaireAnswer
import me.rerere.ai.ui.infoSetQuestionnaireAnswerNotice
import me.rerere.ai.ui.infoSetQuestionnaireNotice
import me.rerere.ai.ui.infoSetQuestionnaireOrNull
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.uuid.Uuid

object QuestionnaireToolNames {
    const val PRESENT = "infoset_questionnaire_present"
    const val ANSWER = "infoset_questionnaire_answer"
}

/**
 * InfoSET 原生问卷工具。
 *
 * 工具输出同时返回一段普通 Text 回执与结构化 Notice：Provider 只需要读 Text，
 * Conversation Archive 会保留 Notice，未来 UI 可独立把它渲染成卡片。
 */
fun buildQuestionnaireTools(
    settings: Settings,
    assistant: Assistant,
    conversationRepository: ConversationRepository,
    conversationId: Uuid,
): List<Tool> = listOf(
    Tool(
        name = QuestionnaireToolNames.PRESENT,
        description = """
            Present an InfoSET questionnaire to the user as a structured timeline event.
            Use this when you want the user to choose, rank, slide, or freely answer something.
            For single/multi/ranking, pass option labels in display order; stable option ids are generated automatically.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", enumSchema("single", "multi", "ranking", "slider", "free_text"))
                    put("question", stringSchema("The question shown to the user."))
                    put("options", buildJsonObject {
                        put("type", "array")
                        put("items", stringSchema("One option label."))
                        put("description", "Required for single, multi, and ranking.")
                    })
                    put("allow_own_answer", booleanSchema("Whether choice questions also accept a custom written answer."))
                    put("slider_min", numberSchema("Minimum slider value."))
                    put("slider_max", numberSchema("Maximum slider value."))
                    put("slider_step", numberSchema("Slider step size."))
                },
                required = listOf("type", "question"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val type = obj.string("type").toQuestionnaireType()
            val labels = obj.stringList("options")
            val questionnaire = createInfoSetQuestionnaire(
                issuerId = assistant.id.toString(),
                issuerLabel = assistant.name.ifBlank { "Aire" },
                recipientId = "user:self",
                recipientLabel = settings.displaySetting.userNickname.ifBlank { "用户" },
                type = type,
                question = obj.string("question"),
                options = labels.mapIndexed { index, label ->
                    InfoSetQuestionOption(id = "o${index + 1}", label = label)
                },
                allowOwnAnswer = obj.boolean("allow_own_answer") ?: false,
                sliderMin = obj.double("slider_min"),
                sliderMax = obj.double("slider_max"),
                sliderStep = obj.double("slider_step"),
            )
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("status", "presented")
                        put("questionnaire_id", questionnaire.id)
                    }.toString()
                ),
                infoSetQuestionnaireNotice(questionnaire),
            )
        },
    ),
    Tool(
        name = QuestionnaireToolNames.ANSWER,
        description = """
            Submit your answer to an existing InfoSET questionnaire as structured data.
            When a questionnaire is addressed to you, use this tool instead of answering only in prose.
            For single/multi use selected_option_ids; ranking uses ranked_option_ids; slider uses slider_value;
            free-text or an allowed own answer uses free_text.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("questionnaire_id", stringSchema("The questionnaire id from the InfoSET questionnaire event."))
                    put("selected_option_ids", stringArraySchema("Selected option ids for single or multi choice."))
                    put("ranked_option_ids", stringArraySchema("All option ids in preferred order for ranking."))
                    put("slider_value", numberSchema("Chosen slider value."))
                    put("free_text", stringSchema("Free-text answer or allowed custom answer."))
                },
                required = listOf("questionnaire_id"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val questionnaireId = obj.string("questionnaire_id")
            val conversation = conversationRepository.getConversationById(conversationId)
                ?: error("Conversation not found")
            val questionnaire = conversation.findInfoSetQuestionnaire(questionnaireId)
                ?: error("Questionnaire not found: $questionnaireId")
            questionnaire.recipientId?.let { recipientId ->
                require(recipientId == assistant.id.toString()) {
                    "Questionnaire $questionnaireId is addressed to another actor"
                }
            }
            val answer = createInfoSetQuestionnaireAnswer(
                questionnaire = questionnaire,
                respondentId = assistant.id.toString(),
                respondentLabel = assistant.name.ifBlank { "Aire" },
                selectedOptionIds = obj.stringList("selected_option_ids"),
                rankedOptionIds = obj.stringList("ranked_option_ids"),
                sliderValue = obj.double("slider_value"),
                freeText = obj.optionalString("free_text"),
            )
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("status", "answered")
                        put("questionnaire_id", questionnaire.id)
                    }.toString()
                ),
                infoSetQuestionnaireAnswerNotice(questionnaire, answer),
            )
        },
    ),
)

internal fun Conversation.findInfoSetQuestionnaire(id: String): InfoSetQuestionnaire? =
    messageNodes.asSequence()
        .flatMap { it.messages.asSequence() }
        .flatMap { it.parts.asSequence() }
        .flatMap { it.questionnaireNotices() }
        .mapNotNull { it.infoSetQuestionnaireOrNull() }
        .firstOrNull { it.id == id }

private fun UIMessagePart.questionnaireNotices(): Sequence<UIMessagePart.Notice> = when (this) {
    is UIMessagePart.Notice -> sequenceOf(this)
    is UIMessagePart.Tool -> output.asSequence().flatMap { it.questionnaireNotices() }
    else -> emptySequence()
}

private fun String.toQuestionnaireType(): InfoSetQuestionnaireType = when (this) {
    "single" -> InfoSetQuestionnaireType.SINGLE
    "multi" -> InfoSetQuestionnaireType.MULTI
    "ranking" -> InfoSetQuestionnaireType.RANKING
    "slider" -> InfoSetQuestionnaireType.SLIDER
    "free_text" -> InfoSetQuestionnaireType.FREE_TEXT
    else -> error("Unsupported questionnaire type: $this")
}

private fun JsonObject.string(key: String): String =
    optionalString(key) ?: error("$key is required")

private fun JsonObject.optionalString(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.stringList(key: String): List<String> =
    (get(key) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        .orEmpty()

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)?.doubleOrNull

private fun stringSchema(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun booleanSchema(description: String) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

private fun numberSchema(description: String) = buildJsonObject {
    put("type", "number")
    put("description", description)
}

private fun stringArraySchema(description: String) = buildJsonObject {
    put("type", "array")
    put("items", buildJsonObject { put("type", "string") })
    put("description", description)
}

private fun enumSchema(vararg values: String) = buildJsonObject {
    put("type", "string")
    put("enum", JsonArray(values.map(::JsonPrimitive)))
}
