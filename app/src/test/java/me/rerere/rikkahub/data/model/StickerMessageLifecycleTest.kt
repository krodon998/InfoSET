package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.INFOSET_STICKER_ASSET_METADATA
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isSticker
import me.rerere.ai.ui.stickerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class StickerMessageLifecycleTest {
    @Test
    fun `sticker message owns only its chat copy while preserving source identity`() {
        val source = "stickers/source.webp"
        val chatCopy = "file:///data/user/0/com.infoset.app/files/upload/copy.webp"
        val image = UIMessagePart.Image(chatCopy, stickerMetadata(source))
        val message = UIMessage(role = MessageRole.USER, parts = listOf(image))
        val conversation = Conversation.ofId(Uuid.random(), messages = listOf(message.toMessageNode()))

        assertTrue(image.isSticker())
        assertEquals(source, image.metadata?.get(INFOSET_STICKER_ASSET_METADATA)?.toString()?.trim('"'))
        assertEquals(setOf(chatCopy), conversation.currentMessages.flatMap { it.parts }.localFileUrls())
        assertFalse(conversation.files.any { it.toString().contains(source) })

        val deleted = conversation.deleteMessage(message.id)!!
        assertTrue(deleted.messageNodes.isEmpty())
        // The reusable source never belonged to the conversation, so message cleanup cannot delete it.
        assertFalse(deleted.files.any { it.toString().contains(source) })
    }
}
