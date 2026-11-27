package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.partIdAt
import me.rerere.ai.ui.withStablePartIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationMessagesTest {
    @Test
    fun `deleting the only message of a node removes the node`() {
        val user = UIMessage.user("hi")
        val reply = UIMessage.assistant("hello")
        val conversation = conversationOf(user.toMessageNode(), reply.toMessageNode())

        val updated = conversation.deleteMessage(reply.id)!!

        assertEquals(listOf(user.id), updated.currentMessages.map { it.id })
    }

    @Test
    fun `deleting the selected last branch selects the previous one`() {
        val first = UIMessage.assistant("first")
        val second = UIMessage.assistant("second")
        val conversation = conversationOf(MessageNode(messages = listOf(first, second), selectIndex = 1))

        val updated = conversation.deleteMessage(second.id)!!

        assertEquals(listOf(first.id), updated.currentMessages.map { it.id })
    }

    @Test
    fun `deleting a missing message returns null`() {
        assertNull(conversationOf(UIMessage.user("hi").toMessageNode()).deleteMessage(Uuid.random()))
    }

    @Test
    fun `editing a message adds a branch that keeps its model`() {
        val modelId = Uuid.random()
        val reply = UIMessage.assistant("old").copy(modelId = modelId)
        val conversation = conversationOf(reply.toMessageNode())

        val node = conversation.editMessage(reply.id, listOf(UIMessagePart.Text("new"))).messageNodes.single()

        assertEquals(2, node.messages.size)
        assertEquals(1, node.selectIndex)
        assertNotEquals(reply.id, node.currentMessage.id)
        assertEquals("new", node.currentMessage.toText())
        assertEquals(MessageRole.ASSISTANT, node.currentMessage.role)
        assertEquals(modelId, node.currentMessage.modelId)
    }

    @Test
    fun `editing split reply preserves every part and its reference`() {
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = Uuid.random(),
            targetPartIndex = 1,
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = "target",
        )
        val original = UIMessage.user("old").copy(annotations = listOf(reference))
        val parts = listOf(
            UIMessagePart.Text("one"),
            UIMessagePart.Text("two"),
            UIMessagePart.Text("three"),
        )

        val edited = conversationOf(original.toMessageNode()).editMessage(original.id, parts).currentMessages.single()

        assertEquals(parts, edited.parts)
        assertEquals(listOf(reference), edited.annotations)
    }

    @Test
    fun `editing a checkpoint rewrites it in place`() {
        val checkpoint = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val conversation = conversationOf(checkpoint.toMessageNode())

        val node = conversation.editMessage(checkpoint.id, listOf(UIMessagePart.Text("edited"))).messageNodes.single()

        assertEquals(listOf(checkpoint.id), node.messages.map { it.id })
        assertTrue(node.currentMessage.isContextCheckpoint)
        assertEquals("edited", node.currentMessage.toText())
    }

    @Test
    fun `conversation save fills legacy part ids without changing message text`() {
        val message = UIMessage.user("legacy")
        val conversation = conversationOf(message.toMessageNode())

        val migrated = conversation.fillPartIds().currentMessages.single()

        assertEquals("legacy", migrated.toText())
        assertEquals(message.id, migrated.id)
        assertTrue(migrated.parts.single().partId != null)
        assertEquals(migrated.partIdAt(0), conversation.fillPartIds().currentMessages.single().partIdAt(0))
    }

    @Test
    fun `editing creates branch owned part identities`() {
        val original = UIMessage.user("old").withStablePartIds()
        val sourcePartId = original.partIdAt(0)
        val editedParts = listOf((original.parts.single() as UIMessagePart.Text).copy(text = "new"))

        val edited = conversationOf(original.toMessageNode())
            .editMessage(original.id, editedParts)
            .fillPartIds()
            .currentMessages
            .single()

        assertNotEquals(original.id, edited.id)
        assertNotEquals(sourcePartId, edited.partIdAt(0))
    }

    @Test
    fun `reply target locator finds an inactive branch and stable part`() {
        val intro = UIMessage.user("intro")
        val target = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("one"), UIMessagePart.Text("two")),
        ).withStablePartIds()
        val targetPartId = target.partIdAt(1)
        val reorderedTarget = target.copy(parts = listOf(target.parts[1], target.parts[0]))
        val activeBranch = UIMessage.assistant("other branch")
        val targetNode = MessageNode(
            messages = listOf(reorderedTarget, activeBranch),
            selectIndex = 1,
        )
        val conversation = conversationOf(intro.toMessageNode(), targetNode)
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = target.id,
            targetPartId = targetPartId,
            targetPartIndex = 1,
            targetRole = target.role,
            speakerSnapshot = "Aire",
            previewSnapshot = "two",
        )

        val location = conversation.locateReplyTarget(reference)!!

        assertEquals(1, location.nodeIndex)
        assertEquals(targetNode.id, location.nodeId)
        assertEquals(0, location.branchIndex)
        assertEquals(0, location.partIndex)
    }

    @Test
    fun `reply target locator returns null after target deletion`() {
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = Uuid.random(),
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = "snapshot",
        )

        assertNull(conversationOf(UIMessage.user("reply").toMessageNode()).locateReplyTarget(reference))
    }

    @Test
    fun `translation is set on the matching message without switching branches`() {
        val first = UIMessage.assistant("first")
        val second = UIMessage.assistant("second")
        val conversation = conversationOf(MessageNode(messages = listOf(first, second), selectIndex = 1))

        val node = conversation.withTranslation(first.id, "翻译").messageNodes.single()

        assertEquals(1, node.selectIndex)
        assertEquals("翻译", node.messages[0].translation)
        assertNull(node.messages[1].translation)
    }

    @Test
    fun `approval is recorded on the pending tool`() {
        val reply = toolReply(ToolApprovalState.Pending)
        val toolCallId = reply.getTools().single().toolCallId
        val conversation = conversationOf(reply.toMessageNode())

        val updated = conversation.withToolApproval(toolCallId, ToolApprovalState.Approved)!!

        assertEquals(ToolApprovalState.Approved, updated.currentMessages.single().getTools().single().approvalState)
    }

    @Test
    fun `approval for a tool that is no longer pending is ignored`() {
        val reply = toolReply(ToolApprovalState.Approved)
        val toolCallId = reply.getTools().single().toolCallId
        val conversation = conversationOf(reply.toMessageNode())

        assertNull(conversation.withToolApproval(toolCallId, ToolApprovalState.Denied("no")))
    }

    @Test
    fun `approval for a tool on an unselected branch is ignored`() {
        val pending = toolReply(ToolApprovalState.Pending)
        val toolCallId = pending.getTools().single().toolCallId
        val conversation = conversationOf(
            MessageNode(messages = listOf(pending, UIMessage.assistant("other")), selectIndex = 1)
        )

        assertNull(conversation.withToolApproval(toolCallId, ToolApprovalState.Approved))
    }

    @Test
    fun `interrupted tools on the last message get a cancelled result`() {
        val conversation = conversationOf(
            UIMessage.user("run it").toMessageNode(),
            toolReply(ToolApprovalState.Approved).toMessageNode(),
        )

        val tool = conversation.cancelInterruptedTools()!!.currentMessages.last().getTools().single()

        assertTrue(tool.isExecuted)
        assertTrue((tool.output.single() as UIMessagePart.Text).text.contains("cancelled"))
    }

    @Test
    fun `nothing to cancel returns null`() {
        val conversation = conversationOf(UIMessage.user("hi").toMessageNode(), UIMessage.assistant("done").toMessageNode())

        assertNull(conversation.cancelInterruptedTools())
        assertNull(Conversation.ofId(Uuid.random()).cancelInterruptedTools())
    }

    private fun conversationOf(vararg nodes: MessageNode) =
        Conversation.ofId(Uuid.random()).copy(messageNodes = nodes.toList())

    private fun toolReply(approvalState: ToolApprovalState) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = Uuid.random().toString(),
                toolName = "workspace_shell",
                input = "{}",
                approvalState = approvalState,
            )
        ),
    )
}
