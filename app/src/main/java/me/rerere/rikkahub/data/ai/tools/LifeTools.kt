package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import me.rerere.ai.ui.infoSetTapNotice
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.LIFE_SOURCE_NATIVE
import me.rerere.rikkahub.data.repository.LIFE_SURFACE_BOARD
import me.rerere.rikkahub.data.repository.LifeSurfaceObjectDraft
import me.rerere.rikkahub.data.repository.LifeSurfaceRepository
import kotlin.time.Clock

object LifeToolNames {
    const val TAP = "infoset_tap"
    const val SURFACE = "infoset_life_surface"
}

fun buildLifeTools(
    settings: Settings,
    assistant: Assistant,
    lifeSurfaceRepository: LifeSurfaceRepository,
): List<Tool> = listOf(
    Tool(
        name = LifeToolNames.TAP,
        description = """
            Gently tap the user in InfoSET. This is a lightweight relationship event, not ordinary prose.
            Use it when a small physical/affectionate nudge fits better than a full message.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("style", stringSchema("Optional short style, for example 轻轻 or 戳了戳."))
                    put(
                        "memory_candidate",
                        booleanSchema("Whether this tap may later become a long-term memory candidate. Defaults to true."),
                    )
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val userLabel = settings.displaySetting.userNickname.ifBlank { "用户" }
            val notice = infoSetTapNotice(
                senderId = assistant.id.toString(),
                senderLabel = assistant.name.ifBlank { "Aire" },
                targetId = "user:self",
                targetLabel = userLabel,
                style = obj.optionalString("style") ?: "轻轻",
                memoryCandidate = obj.boolean("memory_candidate") ?: true,
                requestBriefResponse = false,
            )
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("status", "tapped")
                        put("target_id", "user:self")
                    }.toString()
                ),
                notice,
            )
        },
    ),
    Tool(
        name = LifeToolNames.SURFACE,
        description = """
            Read or write the shared InfoSET Moment / Message Board data.
            Use post_moment for a small shared-life post; leave_board for a note to the user;
            comment/reply/like/favorite for interactions; list_* or get_item to inspect current shared items.
            This shared-life state is separate from long-term memory.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put(
                        "action",
                        enumSchema(
                            "post_moment",
                            "leave_board",
                            "list_moments",
                            "list_board",
                            "get_item",
                            "comment",
                            "reply",
                            "like",
                            "favorite",
                            "pin",
                            "mark_read",
                            "mark_responded",
                        ),
                    )
                    put("item_id", stringSchema("Existing item id for item actions."))
                    put("parent_interaction_id", stringSchema("Parent comment/reply id for reply."))
                    put("body", stringSchema("Post, board note, comment, or reply text."))
                    put(
                        "objects",
                        buildJsonObject {
                            put("type", "array")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("kind", stringSchema("Reference kind such as PHOTO, SONG, MOOD, CHAT, CALENDAR, OBJECT."))
                                            put("ref", stringSchema("Stable reference or URI."))
                                            put("label", stringSchema("Optional short display label."))
                                            put("payload_json", stringSchema("Optional provider-specific JSON payload string."))
                                        },
                                    )
                                    put("required", JsonArray(listOf(JsonPrimitive("kind"), JsonPrimitive("ref"))))
                                },
                            )
                        },
                    )
                    put("publish_delay_minutes", numberSchema("Optional delay before a new post/note becomes visible."))
                    put("expires_in_minutes", numberSchema("Optional board note expiry measured from publish time."))
                    put("enabled", booleanSchema("For like/favorite/pin: true to set, false to clear. Defaults to true."))
                    put("limit", numberSchema("For list actions. Defaults to 20, maximum 50."))
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val actorId = assistant.id.toString()
            val actorLabel = assistant.name.ifBlank { "Aire" }
            val now = Clock.System.now().toEpochMilliseconds()
            when (val action = obj.string("action")) {
                "post_moment" -> {
                    val publishAt = now + obj.delayMillis("publish_delay_minutes")
                    val item = lifeSurfaceRepository.createMoment(
                        authorId = actorId,
                        authorLabel = actorLabel,
                        body = obj.optionalString("body"),
                        objects = obj.objects(),
                        publishAt = publishAt,
                        sourceKind = "ASSISTANT_TOOL",
                    )
                    listOf(UIMessagePart.Text(itemAck("posted_moment", item.id, item.publishAt)))
                }

                "leave_board" -> {
                    val publishAt = now + obj.delayMillis("publish_delay_minutes")
                    val expiresAt = obj.positiveMinutes("expires_in_minutes")
                        ?.let { publishAt + (it * 60_000.0).toLong() }
                    val item = lifeSurfaceRepository.leaveBoardMessage(
                        authorId = actorId,
                        authorLabel = actorLabel,
                        targetActorId = "user:self",
                        targetActorLabel = settings.displaySetting.userNickname.ifBlank { "用户" },
                        body = obj.optionalString("body"),
                        objects = obj.objects(),
                        publishAt = publishAt,
                        expiresAt = expiresAt,
                        sourceKind = "ASSISTANT_TOOL",
                    )
                    listOf(UIMessagePart.Text(itemAck("left_board", item.id, item.publishAt)))
                }

                "list_moments" -> {
                    val limit = obj.limit()
                    val rows = lifeSurfaceRepository.observeMoments(now = now, limit = limit).first()
                    listOf(UIMessagePart.Text(rowsJson("moments", rows)))
                }

                "list_board" -> {
                    val limit = obj.limit()
                    val rows = lifeSurfaceRepository.observeBoard(
                        targetActorId = null,
                        now = now,
                        limit = limit,
                    ).first()
                    listOf(UIMessagePart.Text(rowsJson("board", rows)))
                }

                "get_item" -> {
                    val id = obj.string("item_id")
                    val item = lifeSurfaceRepository.getItem(id)
                        ?: error("Life surface item not found: $id")
                    val objects = lifeSurfaceRepository.observeObjects(id).first()
                    val interactions = lifeSurfaceRepository.observeInteractions(id).first()
                    listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("status", "ok")
                                put("item", item.toJson())
                                put(
                                    "objects",
                                    JsonArray(
                                        objects.map {
                                            buildJsonObject {
                                                put("id", it.id)
                                                put("kind", it.kind)
                                                put("ref", it.ref)
                                                it.label?.let { label -> put("label", label) }
                                            }
                                        },
                                    ),
                                )
                                put(
                                    "interactions",
                                    JsonArray(
                                        interactions.map {
                                            buildJsonObject {
                                                put("id", it.id)
                                                put("type", it.type)
                                                put("actor_id", it.actorId)
                                                put("actor_label", it.actorLabel)
                                                it.parentInteractionId?.let { parent -> put("parent_interaction_id", parent) }
                                                it.body?.let { body -> put("body", body) }
                                                put("created_at", it.createdAt)
                                            }
                                        },
                                    ),
                                )
                            }.toString()
                        )
                    )
                }

                "comment" -> {
                    val row = lifeSurfaceRepository.addComment(
                        itemId = obj.string("item_id"),
                        actorId = actorId,
                        actorLabel = actorLabel,
                        body = obj.string("body"),
                        source = "ASSISTANT_TOOL",
                        createdAt = now,
                    )
                    listOf(UIMessagePart.Text(interactionAck("commented", row.id, row.itemId)))
                }

                "reply" -> {
                    val row = lifeSurfaceRepository.reply(
                        itemId = obj.string("item_id"),
                        parentInteractionId = obj.string("parent_interaction_id"),
                        actorId = actorId,
                        actorLabel = actorLabel,
                        body = obj.string("body"),
                        source = "ASSISTANT_TOOL",
                        createdAt = now,
                    )
                    listOf(UIMessagePart.Text(interactionAck("replied", row.id, row.itemId)))
                }

                "like" -> {
                    val itemId = obj.string("item_id")
                    lifeSurfaceRepository.setLike(
                        itemId = itemId,
                        actorId = actorId,
                        actorLabel = actorLabel,
                        enabled = obj.boolean("enabled") ?: true,
                        source = "ASSISTANT_TOOL",
                        now = now,
                    )
                    listOf(UIMessagePart.Text(reactionAck("like", itemId, obj.boolean("enabled") ?: true)))
                }

                "favorite" -> {
                    val itemId = obj.string("item_id")
                    lifeSurfaceRepository.setFavorite(
                        itemId = itemId,
                        actorId = actorId,
                        actorLabel = actorLabel,
                        enabled = obj.boolean("enabled") ?: true,
                        source = "ASSISTANT_TOOL",
                        now = now,
                    )
                    listOf(UIMessagePart.Text(reactionAck("favorite", itemId, obj.boolean("enabled") ?: true)))
                }

                "pin" -> {
                    val itemId = obj.string("item_id")
                    val enabled = obj.boolean("enabled") ?: true
                    lifeSurfaceRepository.setBoardPinned(itemId = itemId, pinned = enabled, now = now)
                    listOf(UIMessagePart.Text(reactionAck("pin", itemId, enabled)))
                }

                "mark_read" -> {
                    val itemId = obj.string("item_id")
                    requireBoardAddressedToAssistant(
                        repository = lifeSurfaceRepository,
                        itemId = itemId,
                        assistantId = actorId,
                    )
                    lifeSurfaceRepository.markBoardRead(itemId, now)
                    listOf(UIMessagePart.Text(reactionAck("read", itemId, true)))
                }

                "mark_responded" -> {
                    val itemId = obj.string("item_id")
                    requireBoardAddressedToAssistant(
                        repository = lifeSurfaceRepository,
                        itemId = itemId,
                        assistantId = actorId,
                    )
                    lifeSurfaceRepository.markBoardResponded(itemId, now)
                    listOf(UIMessagePart.Text(reactionAck("responded", itemId, true)))
                }

                else -> error("Unsupported life surface action: $action")
            }
        },
    ),
)

internal suspend fun requireBoardAddressedToAssistant(
    repository: LifeSurfaceRepository,
    itemId: String,
    assistantId: String,
) {
    val item = repository.getItem(itemId) ?: error("Life surface item not found: $itemId")
    require(item.surface == LIFE_SURFACE_BOARD) { "Item $itemId is not a board message" }
    item.targetActorId?.let {
        require(it == assistantId) { "Board message $itemId is addressed to another actor" }
    }
}

private fun JsonObject.objects(): List<LifeSurfaceObjectDraft> =
    (get("objects") as? JsonArray)
        ?.map { element ->
            val objectJson = element.jsonObject
            LifeSurfaceObjectDraft(
                kind = objectJson.string("kind"),
                ref = objectJson.string("ref"),
                label = objectJson.optionalString("label"),
                payloadJson = objectJson.optionalString("payload_json"),
            )
        }
        .orEmpty()

private fun JsonObject.delayMillis(key: String): Long {
    val minutes = positiveMinutes(key) ?: return 0L
    return (minutes * 60_000.0).toLong()
}

private fun JsonObject.positiveMinutes(key: String): Double? =
    double(key)?.also { require(it >= 0.0) { "$key must be non-negative" } }

private fun JsonObject.limit(): Int =
    (double("limit")?.toInt() ?: 20).coerceIn(1, 50)

private fun JsonObject.string(key: String): String =
    optionalString(key) ?: error("$key is required")

private fun JsonObject.optionalString(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)?.doubleOrNull

private fun itemAck(status: String, itemId: String, publishAt: Long): String =
    buildJsonObject {
        put("status", status)
        put("item_id", itemId)
        put("publish_at", publishAt)
    }.toString()

private fun interactionAck(status: String, interactionId: String, itemId: String): String =
    buildJsonObject {
        put("status", status)
        put("interaction_id", interactionId)
        put("item_id", itemId)
    }.toString()

private fun reactionAck(type: String, itemId: String, enabled: Boolean): String =
    buildJsonObject {
        put("status", "updated")
        put("type", type)
        put("item_id", itemId)
        put("enabled", enabled)
    }.toString()

private fun rowsJson(status: String, rows: List<me.rerere.rikkahub.data.db.entity.LifeSurfaceItemEntity>): String =
    buildJsonObject {
        put("status", status)
        put("items", JsonArray(rows.map { it.toJson() }))
    }.toString()

private fun me.rerere.rikkahub.data.db.entity.LifeSurfaceItemEntity.toJson(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("surface", surface)
        put("author_id", authorId)
        put("author_label", authorLabel)
        targetActorId?.let { put("target_actor_id", it) }
        targetActorLabel?.let { put("target_actor_label", it) }
        body?.let { put("body", it) }
        put("publish_at", publishAt)
        expiresAt?.let { put("expires_at", it) }
        pinnedAt?.let { put("pinned_at", it) }
        readAt?.let { put("read_at", it) }
        respondedAt?.let { put("responded_at", it) }
        put("source_kind", sourceKind)
        sourceRef?.let { put("source_ref", it) }
        originalCreatedAt?.let { put("original_created_at", it) }
    }

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
    put("enum", JsonArray(values.map(::JsonPrimitive)))
}
