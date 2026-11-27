package me.rerere.rikkahub.data.ai.mcp

import me.rerere.rikkahub.data.ai.account.GitHubAuth
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMcpConfigTest {
    @Test
    fun `official github mcp keeps credentials out of settings`() {
        val unsafe = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                name = "github",
                headers = listOf("Authorization" to "Bearer leaked", "X-Test" to "safe"),
                oauth = McpOAuthState(enabled = true, accessToken = "leaked", refreshToken = "leaked-too"),
            ),
            url = GitHubAuth.MCP_ENDPOINT,
        )
        val settings = Settings().copy(mcpServers = listOf(unsafe))

        val updated = settings.withOfficialGitHubMcp(enabled = true)
        val github = updated.mcpServers.single()

        assertTrue(github.commonOptions.enable)
        assertTrue(github.isOfficialGitHubMcp())
        assertFalse(github.commonOptions.headers.any { it.first.equals("Authorization", true) })
        assertEquals(listOf("X-Test" to "safe"), github.commonOptions.headers)
        assertNull(github.commonOptions.oauth)
        assertTrue(updated.assistants.single { it.id == updated.assistantId }.mcpServers.contains(github.id))
    }

    @Test
    fun `disabling official github mcp detaches selected assistant`() {
        val enabled = Settings().withOfficialGitHubMcp(enabled = true)
        val disabled = enabled.withOfficialGitHubMcp(enabled = false)
        val github = disabled.mcpServers.single(McpServerConfig::isOfficialGitHubMcp)

        assertFalse(github.commonOptions.enable)
        assertFalse(disabled.assistants.single { it.id == disabled.assistantId }.mcpServers.contains(github.id))
    }
}
