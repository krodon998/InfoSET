package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubMcpCallGuardTest {
    @Test
    fun `classifies read branch write build and direct main tools`() {
        assertEquals(GitHubMcpOperation.READ, githubMcpOperation("get_file_contents", "feature/a"))
        assertEquals(GitHubMcpOperation.WRITE, githubMcpOperation("create_or_update_file", "feature/a"))
        assertEquals(GitHubMcpOperation.BUILD, githubMcpOperation("create_pull_request", "feature/a"))
        assertEquals(GitHubMcpOperation.DIRECT_MAIN, githubMcpOperation("merge_pull_request", null))
        assertEquals(GitHubMcpOperation.DIRECT_MAIN, githubMcpOperation("push_files", "main"))
        assertNull(githubMcpOperation("unclassified_tool", null))
    }

    @Test
    fun `extracts controlled repository branch and file paths`() {
        val target = githubMcpTarget(buildJsonObject {
            put("owner", "krodon998")
            put("repo", "InfoSET")
            put("branch", "feature/daddy")
            put("files", buildJsonArray {
                add(buildJsonObject { put("path", "docs/a.md") })
                add(buildJsonObject { put("path", "docs/b.md") })
            })
        })

        assertEquals("krodon998/InfoSET", target?.scope)
        assertEquals("feature/daddy", target?.branch)
        assertEquals(listOf("docs/a.md", "docs/b.md"), target?.paths)
    }

    @Test
    fun `accepts owner slash repository target`() {
        val target = githubMcpTarget(buildJsonObject { put("repository", "krodon998/InfoSET") })

        assertEquals("krodon998", target?.owner)
        assertEquals("InfoSET", target?.repository)
    }
}
