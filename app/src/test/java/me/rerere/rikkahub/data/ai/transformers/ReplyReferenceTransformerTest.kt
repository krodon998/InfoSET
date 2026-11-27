package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.partIdAt
import me.rerere.ai.ui.withStablePartIds
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

        assertEquals("[Replying to Aire | whole message: \"真实原文\"]", (projected.last().parts.first() as UIMessagePart.Text).text)
        assertEquals("宝宝写的正文", reply.toText())
        assertEquals(listOf(UIMessagePart.Text("宝宝写的正文")), reply.parts)
    }

    @Test
    fun `whole-message reply labels its scope and keeps a grapheme-safe excerpt`() {
        val longTail = "尾".repeat(140)
        val target = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("第一颗气泡\n保留换行"),
                UIMessagePart.Text("第二颗气泡 $longTail"),
            ),
        )
        val reply = UIMessage.user("整条回复").copy(
            annotations = listOf(replyTo(target, partIndex = null, snapshot = "旧快照")),
        )

        val prefix = projectReplyReferences(listOf(target, reply)).last().parts.first() as UIMessagePart.Text

        assertEquals(
            "[Replying to Aire | whole message: \"第一颗气泡 保留换行 第二颗气泡 尾尾尾尾尾尾尾尾…\"]",
            prefix.text,
        )
    }

    @Test
    fun `part reply resolves the exact text bubble`() {
        val target = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("一"), UIMessagePart.Text("二"), UIMessagePart.Text("三")),
        )
        val reply = UIMessage.user("接第二句").copy(annotations = listOf(replyTo(target, 1, "snapshot")))

        val prefix = projectReplyReferences(listOf(target, reply)).last().parts.first() as UIMessagePart.Text

        assertEquals("[Replying to Aire | bubble 2: \"二\"]", prefix.text)
    }

    @Test
    fun `part reply labels its bubble and keeps a grapheme-safe excerpt`() {
        val selected = "  单颗气泡第一行\n单颗气泡第二行 😀🎵 ${"密封的信".repeat(30)}\n  "
        val target = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("一"), UIMessagePart.Text(selected), UIMessagePart.Text("三")),
        )
        val reply = UIMessage.user("只回第二颗").copy(annotations = listOf(replyTo(target, 1, "旧快照")))

        val prefix = projectReplyReferences(listOf(target, reply)).last().parts.first() as UIMessagePart.Text

        assertEquals("[Replying to Aire | bubble 2: \"单颗气泡第一行 单颗气泡第二行 😀🎵 密封的信密封…\"]", prefix.text)
    }

    @Test
    fun `part id wins when a persisted target moves to another index`() {
        val original = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("一"), UIMessagePart.Text("二"), UIMessagePart.Text("三")),
        ).withStablePartIds()
        val targetPartId = original.partIdAt(1)!!
        val reordered = original.copy(parts = listOf(original.parts[1], original.parts[0], original.parts[2]))
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = reordered.id,
            targetPartId = targetPartId,
            targetPartIndex = 1,
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = "snapshot",
        )
        val reply = UIMessage.user("仍然回复二").copy(annotations = listOf(reference))

        val prefix = projectReplyReferences(listOf(reordered, reply)).last().parts.first() as UIMessagePart.Text

        assertEquals("[Replying to Aire | bubble 1: \"二\"]", prefix.text)
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
        assertEquals("[Replying to 隐 | bubble 3: \"已经删除的原句\"]", (first.parts.first() as UIMessagePart.Text).text)
        assertFalse(reply.toText().contains("Replying to"))
    }

    @Test
    fun `legacy fallback snapshot is clipped without splitting an emoji`() {
        val snapshot = "字".repeat(24) + "😀" + "尾巴"
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = Uuid.random(),
            targetPartIndex = null,
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = snapshot,
        )
        val reply = UIMessage.user("继续聊").copy(annotations = listOf(reference))

        val prefix = projectReplyReferences(listOf(reply)).single().parts.first() as UIMessagePart.Text

        assertEquals("[Replying to Aire | whole message: \"${"字".repeat(24)}😀…\"]", prefix.text)
    }

    private fun replyTo(target: UIMessage, partIndex: Int?, snapshot: String) =
        UIMessageAnnotation.ReplyReference(
            targetMessageId = target.id,
            targetPartId = partIndex?.let(target::partIdAt),
            targetPartIndex = partIndex,
            targetRole = target.role,
            speakerSnapshot = "Aire",
            previewSnapshot = snapshot,
        )
}
