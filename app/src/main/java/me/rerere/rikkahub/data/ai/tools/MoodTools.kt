package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.MoodRepository
import kotlin.time.Clock

object MoodToolNames {
    const val MOOD = "infoset_mood"
}

fun buildMoodTools(
    assistant: Assistant,
    moodRepository: MoodRepository,
): List<Tool> = listOf(
    Tool(
        name = MoodToolNames.MOOD,
        description = """
            Manage your own current InfoSET mood state.
            Use action=set when your mood meaningfully changes and should persist across turns;
            use action=end when that state has ended. This state is separate from long-term memory.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", enumSchema("set", "end"))
                    put("type", stringSchema("Mood type/name, required for action=set."))
                    put("intensity", numberSchema("Optional intensity."))
                    put("emoji", stringSchema("Optional emoji snapshot."))
                    put("note", stringSchema("Optional short mood line."))
                    put("expected_duration_minutes", numberSchema("Optional expected duration in minutes."))
                    put("visible_to_partner", booleanSchema("Whether the partner may see this mood. Defaults to true."))
                    put("affects_context", booleanSchema("Whether this mood should enter dynamic chat context. Defaults to true."))
                    put("source_ref", stringSchema("Optional source or reason reference, not prose memory."))
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val actorId = assistant.id.toString()
            val actorLabel = assistant.name.ifBlank { "Aire" }
            when (obj.string("action")) {
                "set" -> {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val durationMinutes = obj.double("expected_duration_minutes")
                    require(durationMinutes == null || durationMinutes > 0.0) {
                        "expected_duration_minutes must be positive"
                    }
                    val expectedEndAt = durationMinutes?.let {
                        now + (it * 60_000.0).toLong()
                    }
                    val mood = moodRepository.setMood(
                        actorId = actorId,
                        actorLabel = actorLabel,
                        type = obj.string("type"),
                        intensity = obj.double("intensity"),
                        startedAt = now,
                        expectedEndAt = expectedEndAt,
                        emoji = obj.optionalString("emoji"),
                        note = obj.optionalString("note"),
                        visibleToPartner = obj.boolean("visible_to_partner") ?: true,
                        affectsContext = obj.boolean("affects_context") ?: true,
                        source = "ASSISTANT_TOOL",
                        sourceRef = obj.optionalString("source_ref"),
                    )
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("status", "set")
                                put("mood_id", mood.id)
                                put("type", mood.type)
                            }.toString()
                        )
                    )
                }

                "end" -> {
                    val ended = moodRepository.endMood(actorId)
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("status", "ended")
                                put("ended_count", ended)
                            }.toString()
                        )
                    )
                }

                else -> error("Unsupported mood action")
            }
        },
    )
)

private fun JsonObject.string(key: String): String =
    optionalString(key) ?: error("$key is required")

private fun JsonObject.optionalString(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

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

private fun enumSchema(vararg values: String) = buildJsonObject {
    put("type", "string")
    put("enum", kotlinx.serialization.json.JsonArray(values.map(::JsonPrimitive)))
}
