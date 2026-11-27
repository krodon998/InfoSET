package me.rerere.rikkahub.data.ai.mcp

import me.rerere.rikkahub.data.ai.account.GitHubAuth
import me.rerere.rikkahub.data.datastore.Settings

/**
 * Installs the official GitHub MCP without copying its credential into Settings.
 * The selected assistant receives the server; all other assistants remain unchanged.
 */
internal fun Settings.withOfficialGitHubMcp(enabled: Boolean): Settings {
    val existing = mcpServers.firstOrNull(McpServerConfig::isOfficialGitHubMcp)
    val server = existing?.clone(
        commonOptions = existing.commonOptions.copy(
            enable = enabled,
            name = existing.commonOptions.name.ifBlank { "GitHub · Daddy" },
            headers = existing.commonOptions.headers.filterNot {
                it.first.equals("Authorization", ignoreCase = true)
            },
            oauth = null,
        )
    ) ?: McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(enable = enabled, name = "GitHub · Daddy"),
        url = GitHubAuth.MCP_ENDPOINT,
    )
    val updatedAssistants = assistants.map { assistant ->
        assistant.copy(
            mcpServers = if (enabled && assistant.id == assistantId) {
                assistant.mcpServers + server.id
            } else {
                assistant.mcpServers - server.id
            }
        )
    }
    return copy(
        mcpServers = mcpServers.filterNot(McpServerConfig::isOfficialGitHubMcp) + server,
        assistants = updatedAssistants,
    )
}
