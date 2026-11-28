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
import me.rerere.rikkahub.data.db.entity.SharedStateKinds
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.SharedStateRepository

object SharedStateToolNames {
    const val STATE = "infoset_shared_state"
}

fun buildSharedStateTools(
    assistant: Assistant,
    repository: SharedStateRepository,
): List<Tool> = listOf(
    Tool(
        name = SharedStateToolNames.STATE,
        description = """
            Manage your own InfoSET shared Mood and Moment.
            Mood is how you feel; Moment is what you are doing/thinking right now.
            Both are independent values, but one edit uses one shared duration. Updating only one value
            preserves the other active value and retimes both to the chosen duration.
            Omit duration_minutes to reuse your previous duration; the first ever edit requires it.
            Shared state is temporary context and history, not long-term memory.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", enumSchema("set", "end"))
                    put("mood", stringSchema("Optional mood text or preset display label."))
                    put("mood_preset_key", stringSchema("Optional stable preset key; omit for free-form mood."))
                    put("mood_intensity", numberSchema("Optional mood intensity."))
                    put("mood_emoji", stringSchema("Optional emoji for the mood."))
                    put("moment", stringSchema("Optional short description of what you are doing/thinking now."))
                    put("duration_minutes", numberSchema("Shared duration. Omit to reuse the last chosen duration."))
                    put("end_kind", enumSchema("mood", "moment", "all"))
                    put("visible_to_partner", booleanSchema("Whether your partner may see the updated state. Defaults true."))
                    put("affects_context", booleanSchema("Whether the updated state enters dynamic chat context. Defaults true."))
                    put("source_ref", stringSchema("Optional source/reference id, not prose memory."))
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
                    val durationMinutes = obj.double("duration_minutes")
                    require(durationMinutes == null || durationMinutes > 0.0) {
                        "duration_minutes must be positive"
                    }
                    val snapshot = repository.update(
                        actorId = actorId,
                        actorLabel = actorLabel,
                        mood = obj.optionalString("mood"),
                        moodPresetKey = obj.optionalString("mood_preset_key"),
                        moodIntensity = obj.double("mood_intensity"),
                        moodEmoji = obj.optionalString("mood_emoji"),
                        moment = obj.optionalString("moment"),
                        durationMs = durationMinutes?.let { (it * 60_000.0).toLong() },
                        visibleToPartner = obj.boolean("visible_to_partner") ?: true,
                        affectsContext = obj.boolean("affects_context") ?: true,
                        source = "ASSISTANT_TOOL",
                        sourceRef = obj.optionalString("source_ref"),
                    )
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("status", "set")
                                snapshot.mood?.let { put("mood", it.value) }
                                snapshot.moment?.let { put("moment", it.value) }
                                snapshot.defaultDurationMs?.let { put("duration_ms", it) }
                            }.toString()
                        )
                    )
                }

                "end" -> {
                    val kind = when (obj.optionalString("end_kind") ?: "all") {
                        "mood" -> SharedStateKinds.MOOD
                        "moment" -> SharedStateKinds.MOMENT
                        "all" -> null
                        else -> error("Unsupported end_kind")
                    }
                    val ended = repository.end(actorId, kind)
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("status", "ended")
                                put("ended_count", ended)
                            }.toString()
                        )
                    )
                }

                else -> error("Unsupported shared-state action")
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
