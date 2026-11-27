package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MarkdownMemoryImporterTest {
    @Test
    fun `compatibility import extracts statements without inventing timestamps`() {
        val file = MemoryFile(
            path = "/topics/food.md",
            content = """
                ---
                name: food
                ---

                - [stated] 喜欢黑米粥
                - 偶尔喝奶茶
            """.trimIndent(),
            updatedAt = 99,
        )

        assertEquals(listOf("喜欢黑米粥", "偶尔喝奶茶"), MarkdownMemoryImporter.extract(file))
    }

    @Test
    fun `markdown identity is stable and scoped by memory and source`() {
        val first = AtomicMemoryRepository.stableMarkdownId("a", "/profile.md", "  Lives   in Chengdu ")
        val same = AtomicMemoryRepository.stableMarkdownId("a", "/profile.md", "Lives in Chengdu")
        val otherStore = AtomicMemoryRepository.stableMarkdownId("b", "/profile.md", "Lives in Chengdu")
        val otherPath = AtomicMemoryRepository.stableMarkdownId("a", "/topics/home.md", "Lives in Chengdu")

        assertEquals(first, same)
        assertNotEquals(first, otherStore)
        assertNotEquals(first, otherPath)
    }
}
