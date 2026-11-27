package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class UIMessageSerializationTest {

    @Test
    fun `synthetic marker is not serialized`() {
        val message = UIMessage.user("internal").copy(isSynthetic = true)

        val encoded = Json.encodeToString(message)
        val decoded = Json.decodeFromString<UIMessage>(encoded)

        assertTrue(message.isSynthetic)
        assertFalse(encoded.contains("isSynthetic"))
        assertFalse(decoded.isSynthetic)
    }

    @Test
    fun `context checkpoint marker survives serialization`() {
        val message = UIMessage.user("summary").copy(isContextCheckpoint = true)

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertTrue(decoded.isContextCheckpoint)
    }

    @Test
    fun `messages stored before checkpoints existed decode as ordinary messages`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"user","parts":[]}"""

        assertFalse(Json.decodeFromString<UIMessage>(legacy).isContextCheckpoint)
    }

    @Test
    fun `model snapshot survives serialization`() {
        val modelId = Uuid.random()
        val message = UIMessage.assistant("hi").copy(
            modelId = modelId,
            modelSnapshot = ModelSnapshot(modelId = "gpt-test", displayName = "GPT Test"),
        )

        val shown = Json.decodeFromString<UIMessage>(Json.encodeToString(message)).snapshotModel()

        assertEquals(modelId, shown?.id)
        assertEquals("gpt-test", shown?.modelId)
        assertEquals("GPT Test", shown?.displayName)
    }

    @Test
    fun `messages stored before model snapshots existed decode without one`() {
        val legacy = """{"role":"assistant","parts":[],"modelId":"00000000-0000-0000-0000-000000000001"}"""

        assertNull(Json.decodeFromString<UIMessage>(legacy).snapshotModel())
    }

    @Test
    fun `reply reference and split parts survive serialization`() {
        val targetPartId = Uuid.random()
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = Uuid.random(),
            targetPartId = targetPartId,
            targetPartIndex = 1,
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = "第二个气泡",
        )
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text("第一句"),
                UIMessagePart.Text("第二句"),
                UIMessagePart.Text("第三句"),
            ),
            annotations = listOf(reference),
        )

        val restored = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertEquals(message.parts, restored.parts)
        assertEquals(listOf(reference), restored.annotations)
        assertEquals(targetPartId, restored.annotations.filterIsInstance<UIMessageAnnotation.ReplyReference>().single().targetPartId)
    }

    @Test
    fun `reply reference serializes structured field names and reads legacy names`() {
        val messageId = Uuid.random()
        val reference = UIMessageAnnotation.ReplyReference(
            targetMessageId = messageId,
            targetSegmentIndex = 1,
            scope = ReplyScope.SELECTION,
            targetRole = MessageRole.ASSISTANT,
            speakerSnapshot = "Aire",
            previewSnapshot = "第二段",
        )

        val encoded = Json.encodeToString<UIMessageAnnotation>(reference)
        assertTrue(encoded.contains("\"message_id\":\"$messageId\""))
        assertTrue(encoded.contains("\"scope\":\"selection\""))
        assertTrue(encoded.contains("\"excerpt\":\"第二段\""))

        val legacy = """{"type":"reply_reference","targetMessageId":"$messageId","targetRole":"assistant","speakerSnapshot":"Aire","previewSnapshot":"旧摘要"}"""
        val decoded = Json.decodeFromString<UIMessageAnnotation>(legacy) as UIMessageAnnotation.ReplyReference
        assertEquals(messageId, decoded.targetMessageId)
        assertEquals("旧摘要", decoded.previewSnapshot)
        assertEquals(ReplyScope.WHOLE, decoded.effectiveScope)
    }

    @Test
    fun `part identities survive serialization including nested tool output`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("answer"),
                UIMessagePart.Tool(
                    toolCallId = "call-1",
                    toolName = "test",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("result")),
                ),
            ),
        ).withStablePartIds()

        val restored = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertNotNull(restored.parts[0].partId)
        assertEquals(message.parts[0].partId, restored.parts[0].partId)
        assertEquals(
            (message.parts[1] as UIMessagePart.Tool).output.single().partId,
            (restored.parts[1] as UIMessagePart.Tool).output.single().partId,
        )
    }

    @Test
    fun `legacy parts receive deterministic identities before migrated save`() {
        val messageId = Uuid.parse("00000000-0000-0000-0000-000000000123")
        val legacy = UIMessage(
            id = messageId,
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("legacy"), UIMessagePart.Image("file:///legacy.png")),
        )

        val first = legacy.withStablePartIds()
        val second = Json.decodeFromString<UIMessage>(Json.encodeToString(legacy)).withStablePartIds()

        assertEquals(first.parts.map { it.partId }, second.parts.map { it.partId })
        assertNotNull(first.partIdAt(0))
        assertNotEquals(first.partIdAt(0), first.partIdAt(1))
    }
}
