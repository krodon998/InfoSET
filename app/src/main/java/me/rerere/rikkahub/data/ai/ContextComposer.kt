package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext

/** Context Composer v1 的四类输入及预算结果，供后续调试页和 segment 详情复用。 */
enum class ContextBlockKind {
    CORE_BOND,
    CURRENT_SHARED_STATE,
    OPTIONAL_RECALL,
    TOOL_CONTRACT,
    RECENT_RAW,
}

data class ContextBlockUsage(
    val kind: ContextBlockKind,
    val estimatedTokens: Int,
    val messageCount: Int,
)

data class ComposedContext(
    val messages: List<UIMessage>,
    val blocks: List<ContextBlockUsage>,
) {
    val estimatedTokens: Int = blocks.sumOf { it.estimatedTokens }
}

/**
 * 以 token 预算组装上下文。估算器刻意保持纯函数、跨 Provider 可用；真实 usage 会用于后续校准。
 * 原文只按完整 turn 丢弃，压缩检查点和最近至少 [minimumRecentTurns] 个用户 turn 始终保留。
 */
object ContextComposer {
    fun compose(
        coreBond: UIMessage?,
        currentSharedState: UIMessage?,
        optionalRecall: UIMessage?,
        toolContract: UIMessage?,
        recentRaw: List<UIMessage>,
        totalTokenBudget: Int,
        recallTokenBudget: Int,
        minimumRecentTurns: Int,
        legacyMessageLimit: Int = 0,
    ): ComposedContext {
        val core = coreBond?.takeIf { it.hasContent() }?.let(::listOf).orEmpty()
        val state = currentSharedState?.takeIf { it.hasContent() }?.let(::listOf).orEmpty()
        val recall = optionalRecall
            ?.takeIf { it.hasContent() }
            ?.trimmedToTokenBudget(recallTokenBudget)
            ?.let(::listOf)
            .orEmpty()
        val tools = toolContract?.takeIf { it.hasContent() }?.let(::listOf).orEmpty()
        val fixed = core + state + recall + tools
        val fixedTokens = estimateTokens(fixed)
        val recentBudget = (totalTokenBudget - fixedTokens).coerceAtLeast(0)
        val recent = selectRecentRaw(
            messages = recentRaw.limitContext(legacyMessageLimit),
            tokenBudget = recentBudget,
            minimumRecentTurns = minimumRecentTurns,
        )
        val groups = listOf(
            ContextBlockKind.CORE_BOND to core,
            ContextBlockKind.CURRENT_SHARED_STATE to state,
            ContextBlockKind.OPTIONAL_RECALL to recall,
            ContextBlockKind.TOOL_CONTRACT to tools,
            ContextBlockKind.RECENT_RAW to recent,
        )
        return ComposedContext(
            messages = groups.flatMap { it.second },
            blocks = groups.map { (kind, messages) ->
                ContextBlockUsage(kind, estimateTokens(messages), messages.size)
            },
        )
    }

    fun estimateTokens(messages: List<UIMessage>): Int = messages.sumOf(::estimateTokens)

    fun estimateTokens(message: UIMessage): Int = 4 + message.parts.sumOf(::estimateTokens)

    internal fun estimateTextTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var ascii = 0
        var nonAscii = 0
        text.forEach { if (it.code < 128) ascii++ else nonAscii++ }
        return ((ascii + 3) / 4 + nonAscii).coerceAtLeast(1)
    }

    private fun estimateTokens(part: UIMessagePart): Int = when (part) {
        is UIMessagePart.Text -> estimateTextTokens(part.text)
        is UIMessagePart.Reasoning -> estimateTextTokens(part.reasoning)
        is UIMessagePart.Tool -> estimateTextTokens(part.toolName) + estimateTextTokens(part.input) +
            part.output.sumOf(::estimateTokens) + 16
        is UIMessagePart.ServerTool -> estimateTextTokens(part.toolName) +
            estimateTextTokens(part.input?.toString().orEmpty()) +
            estimateTextTokens(part.output?.toString().orEmpty()) + 16
        is UIMessagePart.ToolCall -> estimateTextTokens(part.toolName) + estimateTextTokens(part.arguments) + 12
        is UIMessagePart.ToolResult -> estimateTextTokens(part.content.toString()) +
            estimateTextTokens(part.arguments.toString()) + 12
        is UIMessagePart.Notice -> estimateTextTokens(part.title) + estimateTextTokens(part.text) + 4
        is UIMessagePart.Image -> 1024
        is UIMessagePart.Video -> 2048
        is UIMessagePart.Audio -> 1024
        is UIMessagePart.Document -> 512
        UIMessagePart.Search -> 8
    }

    private fun selectRecentRaw(
        messages: List<UIMessage>,
        tokenBudget: Int,
        minimumRecentTurns: Int,
    ): List<UIMessage> {
        if (messages.isEmpty()) return emptyList()
        val checkpointIndex = messages.indexOfLast { it.isContextCheckpoint }
        val checkpoint = messages.getOrNull(checkpointIndex)?.takeIf { it.isContextCheckpoint }
        val tail = if (checkpointIndex >= 0) messages.drop(checkpointIndex + 1) else messages
        val chunks = tail.toTurnChunks()
        if (chunks.isEmpty()) return listOfNotNull(checkpoint)

        val checkpointTokens = checkpoint?.let(::estimateTokens) ?: 0
        val tailBudget = (tokenBudget - checkpointTokens).coerceAtLeast(1)
        val chunkTokens = chunks.map(::estimateTokens)
        val total = chunkTokens.sum()
        if (total <= tailBudget) return listOfNotNull(checkpoint) + tail

        // 超限时一次回落到约 50%，之后只追加；再次越过预算才推进下一阶，保持缓存前缀稳定。
        val target = (tailBudget / 2).coerceAtLeast(1)
        val stride = (tailBudget - target).coerceAtLeast(1)
        val stages = (total - tailBudget) / stride + 1
        val dropTarget = stages * stride

        var remainingUserTurns = chunks.count { chunk ->
            chunk.any { it.role == MessageRole.USER && !it.isContextCheckpoint }
        }
        var dropped = 0
        var start = 0
        while (start < chunks.lastIndex && dropped < dropTarget) {
            val chunk = chunks[start]
            val isUserTurn = chunk.any { it.role == MessageRole.USER && !it.isContextCheckpoint }
            if (isUserTurn && remainingUserTurns <= minimumRecentTurns) break
            dropped += chunkTokens[start]
            if (isUserTurn) remainingUserTurns--
            start++
        }
        return listOfNotNull(checkpoint) + chunks.drop(start).flatten()
    }

    private fun List<UIMessage>.toTurnChunks(): List<List<UIMessage>> {
        val result = mutableListOf<MutableList<UIMessage>>()
        for (message in this) {
            if (result.isEmpty() || (message.role == MessageRole.USER && !message.isContextCheckpoint)) {
                result += mutableListOf<UIMessage>()
            }
            result.last() += message
        }
        return result
    }

    private fun UIMessage.hasContent() = parts.any {
        when (it) {
            is UIMessagePart.Text -> it.text.isNotBlank()
            else -> true
        }
    }

    private fun UIMessage.trimmedToTokenBudget(budget: Int): UIMessage? {
        if (budget <= 0) return null
        if (estimateTokens(this) <= budget) return this
        val text = parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
        if (text.isBlank()) return this
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (estimateTextTokens(text.take(mid)) <= budget) low = mid else high = mid - 1
        }
        return copy(parts = listOf(UIMessagePart.Text(text.take(low).trimEnd() + "\n[召回内容已按 token 预算截断]")))
    }
}
