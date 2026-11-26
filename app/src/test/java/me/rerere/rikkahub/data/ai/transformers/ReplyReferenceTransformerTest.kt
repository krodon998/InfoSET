package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class ReplyReferenceTransformerTest {
    @Test
    fun `whole-message reply uses the real target preview without changing archive text`() {
        val target = UIMessage.assistant("真实原文")
        val reference = replyTo(target, partIndex = null, snapshot = "旧快照")
        val reply = UIMessage.user("宝宝写的正文").copy(annotations = listOf(reference))
        val archive = listOf(target, reply)

        val projected = projectReplyReferences(archive)

        assertEquals("[Replying to Aire: \"真实原文\"]", (projected.last().parts.first() as UIMessagePart.Text).text)
        assertEquals("宝宝写的正文", reply.toText())
        assertEquals(listOf(UIMessagePart.Text("宝宝写的正文")), reply.parts)
    }

    @Test
    fun `part reply resolves the exact text bubble`() {
        val target = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("一"), UIMessagePart.Text("二"), UIMessagePart.Text("三")),
        )
        val reply = UIMessage.user("接第二句").copy(annotations = listOf(replyTo(target, 1, "snapshot")))

        val prefix = projectReplyReferences(listOf(target, reply)).last().parts.first() as UIMessagePart.Text

        assertEquals("[Replying to Aire: \"二\"]", prefix.text)
    }

    @Test
    fun `deleted target falls back to persisted snapshot deterministically`() {
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = Uuid.random(),
            targetPartIndex = 2,
            targetRole = MessageRole.USER,
            speakerSnapshot = "隐",
            previewSnapshot = "已经删除的原句",
        )
        val reply = UIMessage.user("仍然看得懂").copy(annotations = listOf(reference))

        val first = projectReplyReferences(listOf(reply)).single()
        val second = projectReplyReferences(listOf(reply)).single()

        assertEquals(first, second)
        assertEquals("[Replying to 隐: \"已经删除的原句\"]", (first.parts.first() as UIMessagePart.Text).text)
        assertFalse(reply.toText().contains("Replying to"))
    }

    private fun replyTo(target: UIMessage, partIndex: Int?, snapshot: String) =
        UIMessageAnnotation.ReplyReference(
            targetMessageId = target.id,
            targetPartIndex = partIndex,
            targetRole = target.role,
            speakerSnapshot = "Aire",
            previewSnapshot = snapshot,
        )
}
