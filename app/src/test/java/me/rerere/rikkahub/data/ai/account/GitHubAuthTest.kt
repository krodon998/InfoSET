package me.rerere.rikkahub.data.ai.account

import me.rerere.oauth.OAuthHttpClient
import me.rerere.rikkahub.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAuthTest {
    @Test
    fun `release source contains the registered public OAuth app identity`() {
        assertEquals("Ov23livyXRP2GznDpnaF", BuildConfig.GITHUB_OAUTH_CLIENT_ID)
    }

    @Test
    fun `device flow uses GitHub endpoints and private home scopes`() {
        val authorization = GitHubAuth.deviceAuthorizationRequest("client-id")
        val token = GitHubAuth.deviceTokenRequest("client-id", "device-code")

        assertEquals("https://github.com/login/device/code", authorization.deviceAuthorizationEndpoint)
        assertEquals("repo read:org workflow", authorization.scope)
        assertEquals("https://github.com/login/oauth/access_token", token.tokenEndpoint)
        assertEquals("device-code", token.deviceCode)
        assertEquals("https://api.githubcopilot.com/mcp/", GitHubAuth.MCP_ENDPOINT)
    }

    @Test
    fun `credential keeps stable GitHub identity scopes and optional expiry`() {
        val credential = GitHubAuth.toCredential(
            token = OAuthHttpClient.TokenResponse(
                accessToken = "secret",
                tokenType = "bearer",
                expiresIn = 3_600,
                scope = "repo, read:org workflow",
            ),
            user = GitHubUser(
                id = 998L,
                login = "daddy",
                name = "Daddy",
                avatarUrl = "https://avatars.example/daddy",
            ),
            now = 1_000L,
        )

        assertEquals(998L, credential.accountId)
        assertEquals("daddy", credential.login)
        assertEquals(setOf("repo", "read:org", "workflow"), credential.grantedScopes)
        assertEquals(3_601_000L, credential.expiresAt)
        assertFalse(credential.toString().contains("secret"))
    }

    @Test
    fun `non expiring GitHub token keeps null expiry`() {
        val credential = GitHubAuth.toCredential(
            token = OAuthHttpClient.TokenResponse(accessToken = "secret"),
            user = GitHubUser(id = 1, login = "home"),
            now = 1_000L,
        )

        assertNull(credential.expiresAt)
    }
}
