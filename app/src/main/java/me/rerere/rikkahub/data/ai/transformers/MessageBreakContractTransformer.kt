package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageBreakConfig
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

object MessageBreakContractTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.settings.displaySetting.enableAssistantMessageBreaks) return messages
        return injectMessageBreakContract(messages, ctx.settings.displaySetting.messageBreakMaxSegments)
    }
}

internal fun injectMessageBreakContract(messages: List<UIMessage>, maxSegments: Int): List<UIMessage> {
    val contract = buildMessageBreakContract(maxSegments)
    val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
    if (systemIndex == -1) return listOf(UIMessage.system(contract).copy(isSynthetic = true)) + messages
    return messages.toMutableList().apply {
        val system = get(systemIndex)
        val parts = system.parts.toMutableList()
        val textIndex = parts.indexOfFirst { it is UIMessagePart.Text }
        if (textIndex == -1) {
            parts.add(0, UIMessagePart.Text(contract))
        } else {
            val text = parts[textIndex] as UIMessagePart.Text
            parts[textIndex] = text.copy(text = text.text + "\n\n" + contract)
        }
        set(systemIndex, system.copy(parts = parts, isSynthetic = true))
    }
}

internal fun buildMessageBreakContract(maxSegments: Int): String = """
    <message_break_contract>
    When a reply contains separate conversational beats, put ${MessageBreakConfig.TAG} on its own line with no surrounding spaces.
    - Use at most ${maxSegments.coerceAtLeast(1)} segments in one reply. Do not use the marker merely for visual layout.
    - Split only between complete, semantically independent blocks. Never split a sentence.
    - Never put the marker inside fenced code (``` or ~~~), inline code, tables, list items, or block quotes.
    - To display the literal marker itself, write \${MessageBreakConfig.TAG}.
    - Omit the marker when one conversational bubble is enough.
    </message_break_contract>
""".trimIndent()
