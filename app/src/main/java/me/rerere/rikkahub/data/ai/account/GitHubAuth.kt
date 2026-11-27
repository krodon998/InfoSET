package me.rerere.rikkahub.data.ai.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.oauth.OAuthHttpClient

/** GitHub OAuth device flow and identity projection used by InfoSET's home account. */
internal object GitHubAuth {
    const val DEVICE_AUTHORIZATION_ENDPOINT = "https://github.com/login/device/code"
    const val TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token"
    const val USER_ENDPOINT = "https://api.github.com/user"
    const val MCP_ENDPOINT = "https://api.githubcopilot.com/mcp/"

    // Private repositories, organization discovery, Actions and workflow-file changes are
    // needed by the first private-home implementation. InfoSET capability grants still gate
    // each operation independently after GitHub has authenticated the account.
    const val DEFAULT_SCOPE = "repo read:org workflow"

    fun deviceAuthorizationRequest(clientId: String) = OAuthHttpClient.DeviceAuthorizationRequest(
        deviceAuthorizationEndpoint = DEVICE_AUTHORIZATION_ENDPOINT,
        clientId = requireClientId(clientId),
        scope = DEFAULT_SCOPE,
    )

    fun deviceTokenRequest(clientId: String, deviceCode: String) = OAuthHttpClient.DeviceTokenRequest(
        tokenEndpoint = TOKEN_ENDPOINT,
        clientId = requireClientId(clientId),
        deviceCode = deviceCode,
    )

    fun toCredential(
        token: OAuthHttpClient.TokenResponse,
        user: GitHubUser,
        now: Long = System.currentTimeMillis(),
    ) = GitHubAccountCredential(
        accountId = user.id,
        login = user.login,
        displayName = user.name,
        avatarUrl = user.avatarUrl,
        accessToken = token.accessToken,
        tokenType = token.tokenType,
        grantedScopes = token.scope
            .orEmpty()
            .split(',', ' ')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet(),
        refreshToken = token.refreshToken,
        createdAt = now,
        expiresAt = token.expiresIn?.let { now + it * 1_000L },
    )

    private fun requireClientId(clientId: String): String =
        clientId.trim().also { require(it.isNotEmpty()) { "GitHub OAuth client id is not configured" } }
}

@Serializable
internal data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GitHubAccountCredential(
    val accountId: Long,
    val login: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String,
    val tokenType: String = "Bearer",
    val grantedScopes: Set<String> = emptySet(),
    val refreshToken: String? = null,
    val createdAt: Long,
    val expiresAt: Long? = null,
) {
    val isExpired: Boolean
        get() = expiresAt?.let { System.currentTimeMillis() >= it } == true

    override fun toString(): String =
        "GitHubAccountCredential(accountId=$accountId, login=$login, grantedScopes=$grantedScopes, " +
            "createdAt=$createdAt, expiresAt=$expiresAt)"
}
