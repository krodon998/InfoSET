package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.DynamicPostRepository

object NowToolNames {
    const val POST = "infoset_now_post"
    const val INTERACT = "infoset_now_interact"
}

/**
 * 「此刻」动态的模型工具。
 *
 * Mood / Moment 由 infoset_shared_state 管理；这里仅管理动态帖子与互动。
 * 当前 Tool 先做纯文字帖子，媒体/跨 App 对象由 App UI 或后续 ObjectRef Tool 进入同一 Repository。
 */
fun buildNowTools(
    assistant: Assistant,
    repository: DynamicPostRepository,
): List<Tool> = listOf(
    Tool(
        name = NowToolNames.POST,
        description = """
            Manage your own InfoSET Now dynamic posts.
            Actions: publish, draft, schedule, update_pending, publish_pending, edit_body,
            archive, restore, delete. Scheduled posts keep the planned time, while published_at
            records the actual publication time. Published media/object attachments are frozen;
            this text tool only creates/edits post body.
        """.trimIndent().replace("
", " "),
        needsApproval = {
            (it.jsonObject["action"] as? JsonPrimitive)?.contentOrNull == "delete"
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", enumSchema(
                        "publish", "draft", "schedule", "update_pending", "publish_pending",
                        "edit_body", "archive", "restore", "delete"
                    ))
                    put("post_id", stringSchema("Existing post id for update actions."))
                    put("body", stringSchema("Post text."))
                    put("scheduled_at_epoch_ms", integerSchema("Future scheduled publication time in epoch milliseconds."))
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val actorId = assistant.id.toString()
            val actorLabel = assistant.name.ifBlank { "Aire" }
            val post = when (obj.string("action")) {
                "publish" -> repository.publishNow(
                    authorId = actorId,
                    authorLabel = actorLabel,
                    body = obj.optionalString("body"),
                )
                "draft" -> repository.createDraft(
                    authorId = actorId,
                    authorLabel = actorLabel,
                    body = obj.optionalString("body"),
                )
                "schedule" -> repository.schedule(
                    authorId = actorId,
                    authorLabel = actorLabel,
                    body = obj.optionalString("body"),
                    scheduledAt = obj.long("scheduled_at_epoch_ms"),
                )
                "update_pending" -> {
                    val postId = obj.string("post_id")
                    val current = requireNotNull(repository.getPost(postId)) { "post not found" }
                    repository.updatePending(
                        postId = postId,
                        actorId = actorId,
                        body = if ("body" in obj) obj.optionalString("body") else current.body,
                        objects = null,
                        scheduledAt = obj.optionalLong("scheduled_at_epoch_ms"),
                    )
                }
                "publish_pending" -> repository.publishPending(
                    postId = obj.string("post_id"),
                    actorId = actorId,
                )
                "edit_body" -> repository.editPublishedBody(
                    postId = obj.string("post_id"),
                    actorId = actorId,
                    body = obj.optionalString("body"),
                )
                "archive" -> repository.archive(
                    postId = obj.string("post_id"),
                    actorId = actorId,
                )
                "restore" -> repository.restoreArchive(
                    postId = obj.string("post_id"),
                    actorId = actorId,
                )
                "delete" -> repository.deletePost(
                    postId = obj.string("post_id"),
                    actorId = actorId,
                )
                else -> error("Unsupported now post action")
            }
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("status", post.status.lowercase())
                        put("post_id", post.id)
                        post.scheduledAt?.let { put("scheduled_at", it) }
                        post.publishedAt?.let { put("published_at", it) }
                    }.toString()
                )
            )
        },
    ),
    Tool(
        name = NowToolNames.INTERACT,
        description = """
            Interact with a published InfoSET Now post.
            reaction sets your single current reaction; omit reaction to remove it.
            favorite toggles your own favorite. comment/reply bodies are immutable after posting;
            delete_comment removes your own comment text while preserving its stable thread identity.
        """.trimIndent().replace("
", " "),
        needsApproval = {
            (it.jsonObject["action"] as? JsonPrimitive)?.contentOrNull == "delete_comment"
        },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", enumSchema("reaction", "favorite", "comment", "reply", "delete_comment"))
                    put("post_id", stringSchema("Target post id."))
                    put("interaction_id", stringSchema("Parent reply id or comment/reply id to delete."))
                    put("body", stringSchema("Comment or reply text."))
                    put("reaction", stringSchema("Reaction key, e.g. LIKE or HEART. Omit to remove your reaction."))
                    put("enabled", booleanSchema("Favorite state."))
                },
                required = listOf("action"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val actorId = assistant.id.toString()
            val actorLabel = assistant.name.ifBlank { "Aire" }
            when (obj.string("action")) {
                "reaction" -> {
                    val postId = obj.string("post_id")
                    repository.setReaction(
                        postId = postId,
                        actorId = actorId,
                        actorLabel = actorLabel,
                        reaction = obj.optionalString("reaction"),
                    )
                    simpleResult("reaction", postId)
                }
                "favorite" -> {
                    val postId = obj.string("post_id")
                    repository.setFavorite(
                        postId = postId,
                        actorId = actorId,
                        actorLabel = actorLabel,
                        enabled = obj.boolean("enabled") ?: error("enabled is required"),
                    )
                    simpleResult("favorite", postId)
                }
                "comment" -> {
                    val postId = obj.string("post_id")
                    val interaction = repository.addComment(
                        postId = postId,
                        actorId = actorId,
                        actorLabel = actorLabel,
                        body = obj.string("body"),
                    )
                    interactionResult("commented", postId, interaction.id)
                }
                "reply" -> {
                    val postId = obj.string("post_id")
                    val interaction = repository.reply(
                        postId = postId,
                        parentInteractionId = obj.string("interaction_id"),
                        actorId = actorId,
                        actorLabel = actorLabel,
                        body = obj.string("body"),
                    )
                    interactionResult("replied", postId, interaction.id)
                }
                "delete_comment" -> {
                    val postId = obj.string("post_id")
                    val interactionId = obj.string("interaction_id")
                    repository.deleteComment(
                        interactionId = interactionId,
                        actorId = actorId,
                    )
                    interactionResult("deleted", postId, interactionId)
                }
                else -> error("Unsupported now interaction action")
            }
        },
    ),
)

private fun simpleResult(status: String, postId: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(buildJsonObject {
        put("status", status)
        put("post_id", postId)
    }.toString()))

private fun interactionResult(status: String, postId: String, interactionId: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(buildJsonObject {
        put("status", status)
        put("post_id", postId)
        put("interaction_id", interactionId)
    }.toString()))

private fun JsonObject.string(key: String): String =
    optionalString(key) ?: error("$key is required")

private fun JsonObject.optionalString(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.long(key: String): Long =
    optionalLong(key) ?: error("$key is required")

private fun JsonObject.optionalLong(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun stringSchema(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun integerSchema(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun booleanSchema(description: String) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

private fun enumSchema(vararg values: String) = buildJsonObject {
    put("type", "string")
    put("enum", kotlinx.serialization.json.JsonArray(values.map(::JsonPrimitive)))
}
