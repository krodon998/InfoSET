package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.util.json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
sealed class ToolApprovalState {
    @Serializable
    @SerialName("auto")
    data object Auto : ToolApprovalState()

    @Serializable
    @SerialName("pending")
    data object Pending : ToolApprovalState()

    @Serializable
    @SerialName("approved")
    data object Approved : ToolApprovalState()

    @Serializable
    @SerialName("denied")
    data class Denied(val reason: String = "") : ToolApprovalState()

    @Serializable
    @SerialName("answered")
    data class Answered(val answer: String) : ToolApprovalState()
}

fun ToolApprovalState.canResumeToolExecution(): Boolean {
    return when (this) {
        ToolApprovalState.Approved -> true
        is ToolApprovalState.Denied -> true
        is ToolApprovalState.Answered -> true
        ToolApprovalState.Auto,
        ToolApprovalState.Pending,
        -> false
    }
}

/**
 * 服务端工具调用的通用生命周期状态。
 *
 * Provider 返回的更细粒度状态应保存在 [UIMessagePart.ServerTool.metadata] 中，避免在通用层绑定具体协议。
 */
@Serializable
enum class ServerToolStatus {
    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED,
}

/** The kind of text carried by a reasoning part. */
@Serializable
enum class ReasoningType {
    @SerialName("reasoning_text")
    REASONING_TEXT,

    @SerialName("summary_text")
    SUMMARY_TEXT,
}

@Serializable
sealed class UIMessagePart {
    abstract val metadata: JsonObject?
    abstract val partId: Uuid?

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Serializable
    @SerialName("video")
    data class Video(
        val url: String,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Serializable
    @SerialName("audio")
    data class Audio(
        val url: String,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Serializable
    @SerialName("document")
    data class Document(
        val url: String,
        val fileName: String,
        val mime: String = "text/*",
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val reasoning: String,
        val createdAt: Instant = Clock.System.now(),
        val finishedAt: Instant? = Clock.System.now(),
        override var metadata: JsonObject? = null,
        val reasoningType: ReasoningType = ReasoningType.REASONING_TEXT,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    @Deprecated("Deprecated")
    @Serializable
    @SerialName("search")
    data object Search : UIMessagePart() {
        override var metadata: JsonObject? = null
        override val partId: Uuid? = null
    }

    @Deprecated("Use UIMessagePart.Tool instead")
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart() {
        fun merge(other: ToolCall): ToolCall {
            return ToolCall(
                toolCallId = toolCallId,
                toolName = toolName + other.toolName,
                arguments = arguments + other.arguments,
                approvalState = approvalState,
                metadata = if (other.metadata != null) other.metadata else metadata,
                partId = partId,
            )
        }
    }

    @Deprecated("Use UIMessagePart.Tool instead")
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val content: JsonElement,
        val arguments: JsonElement,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()

    /**
     * 由 Provider 在服务端执行的工具调用，例如网页搜索、文件检索或代码执行。
     *
     * 与 [Tool] 不同，此类型只追踪调用及结果，不参与客户端审批或执行流程。[input] 和 [output]
     * 使用通用 JSON，以容纳不同 Provider、不同工具的协议结构；需要原样回传的 Provider 数据也应保留在其中。
     */
    @Serializable
    @SerialName("server_tool")
    data class ServerTool(
        val toolCallId: String,
        val toolName: String,
        val input: JsonElement? = null,
        val output: JsonElement? = null,
        val status: ServerToolStatus,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart() {
        val isFinished: Boolean
            get() = status == ServerToolStatus.COMPLETED || status == ServerToolStatus.FAILED
    }

    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: List<UIMessagePart> = emptyList(),
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
        override var metadata: JsonObject? = null,
        // 本地执行的起止时间，只统计真正运行工具的耗时；拒绝、问答和旧消息都为空
        val startedAt: Instant? = null,
        val finishedAt: Instant? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart() {
        /** Whether the tool has been executed (has output) */
        val isExecuted: Boolean get() = output.isNotEmpty()

        /** Whether the tool is pending user approval */
        val isPending: Boolean get() = !isExecuted && approvalState is ToolApprovalState.Pending

        /** Whether generation can resume and handle this tool immediately */
        val canResumeExecution: Boolean get() = !isExecuted && approvalState.canResumeToolExecution()

        /** Parse input string as JsonElement */
        fun inputAsJson(): JsonElement = runCatching {
            json.parseToJsonElement(input.ifBlank { "{}" })
        }.getOrElse { JsonObject(emptyMap()) }

        fun merge(other: Tool): Tool {
            return Tool(
                toolCallId = toolCallId,
                toolName = toolName + other.toolName,
                input = input + other.input,
                output = output + other.output,
                approvalState = approvalState,
                metadata = if (other.metadata != null) other.metadata else metadata,
                startedAt = startedAt,
                finishedAt = finishedAt,
                partId = partId,
            )
        }
    }

    /**
     * 运行过程中要告诉模型的事件，例如后台命令结束。
     *
     * 生成中由 agent 循环在轮到模型回复时追加到助手消息里，开始新的一轮时单独存成一条 user 消息，
     * 和其它 part 一样保存在会话中；
     * 请求时由 [me.rerere.ai.agent.expandNotices] 在所在位置拆成一条 user 消息，前缀固定，可以命中提示词缓存。
     * [title] 只用于界面展示，[text] 是发给模型的内容。
     */
    @Serializable
    @SerialName("notice")
    data class Notice(
        val title: String,
        val text: String,
        override var metadata: JsonObject? = null,
        override val partId: Uuid? = null,
    ) : UIMessagePart()
}

/** Return the same logical part with a persisted identity. */
fun UIMessagePart.withPartId(id: Uuid?): UIMessagePart = when (this) {
    is UIMessagePart.Text -> copy(partId = id)
    is UIMessagePart.Image -> copy(partId = id)
    is UIMessagePart.Video -> copy(partId = id)
    is UIMessagePart.Audio -> copy(partId = id)
    is UIMessagePart.Document -> copy(partId = id)
    is UIMessagePart.Reasoning -> copy(partId = id)
    UIMessagePart.Search -> this
    is UIMessagePart.ToolCall -> copy(partId = id)
    is UIMessagePart.ToolResult -> copy(partId = id)
    is UIMessagePart.ServerTool -> copy(partId = id)
    is UIMessagePart.Tool -> copy(partId = id)
    is UIMessagePart.Notice -> copy(partId = id)
}

const val INFOSET_KIND_METADATA = "infoset_kind"
const val INFOSET_STICKER_KIND = "sticker"
const val INFOSET_STICKER_ASSET_METADATA = "infoset_sticker_asset"

fun stickerMetadata(assetRelativePath: String): JsonObject = buildJsonObject {
    put(INFOSET_KIND_METADATA, JsonPrimitive(INFOSET_STICKER_KIND))
    put(INFOSET_STICKER_ASSET_METADATA, JsonPrimitive(assetRelativePath))
}

fun UIMessagePart.Image.isSticker(): Boolean =
    (metadata?.get(INFOSET_KIND_METADATA) as? JsonPrimitive)?.contentOrNull == INFOSET_STICKER_KIND
