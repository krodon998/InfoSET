package me.rerere.rikkahub.data.search

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ArchiveBrowserTest {
    private val date = LocalDate(2026, 11, 28)

    @Test
    fun attachmentProjectionKeepsMessageAndPartIdentity() {
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text("look https://example.com/a"),
                UIMessagePart.Image("file:///image.png"),
                UIMessagePart.Video("file:///video.mp4"),
                UIMessagePart.Document("file:///notes.pdf", "notes.pdf", "application/pdf"),
            ),
            createdAt = LocalDateTime(2026, 11, 28, 1, 2, 3),
        )
        val node = MessageNode(id = Uuid.random(), messages = listOf(message))
        val conversation = Conversation(
            assistantId = Uuid.random(),
            title = "home",
            messageNodes = listOf(node),
        )

        for (kind in listOf(
            ArchiveBrowseKind.IMAGE,
            ArchiveBrowseKind.VIDEO,
            ArchiveBrowseKind.FILE,
            ArchiveBrowseKind.LINK,
        )) {
            val result = conversation.projectArchive(kind, date).single()
            assertEquals(conversation.id.toString(), result.conversationId)
            assertEquals(node.id.toString(), result.nodeId)
            assertEquals(message.id.toString(), result.messageId)
            assertNotNull(result.partId)
        }
    }

    @Test
    fun dateProjectionUsesOriginalMessageTimeAndIncludesBranches() {
        val first = UIMessage.user("first").copy(
            createdAt = LocalDateTime(2026, 11, 27, 23, 59, 0),
        )
        val matching = UIMessage.assistant("matching").copy(
            createdAt = LocalDateTime(2026, 11, 28, 0, 1, 0),
        )
        val branch = UIMessage.assistant("branch").copy(
            createdAt = LocalDateTime(2026, 11, 28, 0, 2, 0),
        )
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode(messages = listOf(first)),
                MessageNode(messages = listOf(matching, branch), selectIndex = 0),
            ),
        )

        val results = conversation.projectArchive(ArchiveBrowseKind.MESSAGE, date)

        assertEquals(setOf(matching.id.toString(), branch.id.toString()), results.map { it.messageId }.toSet())
        assertTrue(results.all { it.partId != null })
        assertEquals(branch.id.toString(), results.first().messageId)
    }
}
