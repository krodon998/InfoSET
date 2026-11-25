package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.MailboxLetterDAO
import me.rerere.rikkahub.data.db.entity.MailboxLetterEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

private val MAILBOX_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

internal fun buildMailboxWriteTool(
    mailboxLetterDAO: MailboxLetterDAO,
    nowMillis: () -> Long = System::currentTimeMillis,
): Tool = Tool(
    name = "mailbox_write_letter",
    description = """
        Place a private letter in the user's InfoSET mailbox for a future date.
        Use this only when deliberately sending words that should arrive later.
        The user can see that a letter is waiting, while its title and contents remain hidden until unlock_at.
        Date-only and offset-free values use Asia/Shanghai time. The result never repeats the private letter body.
        After scheduling, do not quote or summarize the title or body in chat; only say that the letter was sealed.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 120)
                    put("description", "Title revealed only after the letter arrives.")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 12000)
                    put("description", "Private letter body, hidden until the unlock time.")
                })
                put("unlock_at", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Future delivery time as yyyy-MM-dd, ISO-8601 date-time, or epoch milliseconds. " +
                            "Dates and local date-times use Asia/Shanghai."
                    )
                })
                put("summary", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 240)
                    put("description", "Optional short summary shown after arrival.")
                })
                put("salutation", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 160)
                    put("description", "Optional greeting shown inside the opened letter.")
                })
                put("signature", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 160)
                    put("description", "Optional sign-off shown inside the opened letter.")
                })
                put("source_ref", buildJsonObject {
                    put("type", "string")
                    put("maxLength", 512)
                    put("description", "Optional stable source reference for later tracing.")
                })
            },
            required = listOf("title", "body", "unlock_at"),
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val title = params.string("title")?.trim()
        val body = params.string("body")
        val unlockRaw = params.string("unlock_at")?.trim()

        if (title.isNullOrEmpty() || body.isNullOrBlank() || unlockRaw.isNullOrEmpty()) {
            return@Tool mailboxToolResult(
                error = "MISSING_REQUIRED",
                message = "title, body, and unlock_at are required.",
            )
        }
        if (title.length > 120 || body.length > 12000) {
            return@Tool mailboxToolResult(
                error = "CONTENT_TOO_LONG",
                message = "title must be at most 120 characters and body at most 12000 characters.",
            )
        }

        val summary: String?
        val salutation: String?
        val signature: String?
        val sourceRef: String?
        try {
            summary = params.optionalString("summary", 240)
            salutation = params.optionalString("salutation", 160)
            signature = params.optionalString("signature", 160)
            sourceRef = params.optionalString("source_ref", 512)
        } catch (e: IllegalArgumentException) {
            return@Tool mailboxToolResult("CONTENT_TOO_LONG", e.message ?: "Optional content is too long.")
        }

        val unlockAt = try {
            parseMailboxUnlockAt(unlockRaw)
        } catch (e: IllegalArgumentException) {
            return@Tool mailboxToolResult("INVALID_TIME", e.message ?: "Invalid unlock_at.")
        }
        val writtenAt = nowMillis()
        if (unlockAt <= writtenAt) {
            return@Tool mailboxToolResult("TIME_NOT_FUTURE", "unlock_at must be in the future.")
        }

        val id = UUID.randomUUID().toString()
        mailboxLetterDAO.insert(
            MailboxLetterEntity(
                id = id,
                title = title,
                summary = summary,
                salutation = salutation,
                body = body,
                signature = signature,
                unlockAt = unlockAt,
                writtenAt = writtenAt,
                openedAt = null,
                source = "ai",
                sourceRef = sourceRef,
            )
        )

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("status", "scheduled")
                    put("id", id)
                    put("unlock_at", Instant.ofEpochMilli(unlockAt).atZone(MAILBOX_ZONE).toString())
                }.toString()
            )
        )
    },
)

internal fun parseMailboxUnlockAt(value: String): Long {
    value.toLongOrNull()?.let { return it }

    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(MAILBOX_ZONE).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(MAILBOX_ZONE).toInstant() }.getOrNull()
        ?: throw IllegalArgumentException(
            "unlock_at must be yyyy-MM-dd, an ISO-8601 date-time, or epoch milliseconds."
        )
    return instant.toEpochMilli()
}

private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonObject.optionalString(key: String, maxLength: Int): String? {
    val raw = string(key)?.trim().orEmpty()
    require(raw.length <= maxLength) { "$key must be at most $maxLength characters." }
    return raw.ifEmpty { null }
}

private fun mailboxToolResult(error: String, message: String): List<UIMessagePart> = listOf(
    UIMessagePart.Text(
        buildJsonObject {
            put("error", error)
            put("message", message)
        }.toString()
    )
)
