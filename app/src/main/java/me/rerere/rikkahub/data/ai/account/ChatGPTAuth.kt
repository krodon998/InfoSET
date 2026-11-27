package me.rerere.rikkahub.data.ai.account

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.provider.OAuthCredential
import me.rerere.oauth.OAuthHttpClient

/**
 * Official Sign in with ChatGPT protocol for third-party agents.
 */
internal object ChatGPTAuth {
    private const val ISSUER = "https://auth.openai.com"
    const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
    const val AUTHORIZATION_ENDPOINT = "$ISSUER/api/accounts/authorize"
    const val TOKEN_ENDPOINT = "$ISSUER/api/accounts/oauth/token"
    const val RESOURCE = "https://api.openai.com/v1"
    const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val AGENT_NAME = "InfoSET Daddy Home"
    const val CALLBACK_PORT = 1455
    const val CALLBACK_PATH = "/auth/callback"
    const val CALLBACK_HOST = "localhost"

    fun authorizationRequest(
        pkce: OAuthHttpClient.Pkce,
        state: String,
        redirectUri: String,
        clientId: String,
        hostId: String,
        nonce: String,
    ) = OAuthHttpClient.AuthorizationRequest(
        authorizationEndpoint = AUTHORIZATION_ENDPOINT,
        clientId = clientId,
        redirectUri = redirectUri,
        pkce = pkce,
        state = state,
        scope = SCOPE,
        resources = listOf(RESOURCE),
        additionalParameters = mapOf(
            "nonce" to nonce,
            "ext_agent_host_id" to hostId,
            "agent_name_hint" to AGENT_NAME,
        ),
    )

    fun tokenRequest(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ) = OAuthHttpClient.AuthorizationCodeTokenRequest(
        tokenEndpoint = TOKEN_ENDPOINT,
        clientId = clientId,
        code = code,
        codeVerifier = codeVerifier,
        redirectUri = redirectUri,
        resources = listOf(RESOURCE),
    )

    fun refreshRequest(refreshToken: String, clientId: String) = OAuthHttpClient.RefreshTokenRequest(
        tokenEndpoint = TOKEN_ENDPOINT,
        clientId = clientId,
        refreshToken = refreshToken,
        scope = SCOPE,
        resources = listOf(RESOURCE),
    )

    fun requireValidIdToken(idToken: String?, clientId: String, nonce: String, nowSeconds: Long) {
        val claims = idToken?.let(::decodeJwtPayload) ?: error("OpenAI did not return a valid ID token")
        require(claims.claimString("iss") == ISSUER) { "Unexpected OpenAI ID token issuer" }
        val audiences = runCatching { claims["aud"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }
            .getOrNull() ?: listOfNotNull(claims.claimString("aud"))
        require(clientId in audiences) { "OpenAI ID token audience mismatch" }
        require(claims.claimString("nonce") == nonce) { "OpenAI ID token nonce mismatch" }
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull ?: error("OpenAI ID token has no expiry")
        require(expiresAt > nowSeconds) { "OpenAI ID token expired" }
        require(!claims.claimString("sub").isNullOrBlank()) { "OpenAI ID token has no subject" }
    }

    /**
     * 把令牌响应转成凭据。刷新响应可能不带 id_token 或 refresh_token，此时沿用 [previous] 里的值。
     */
    fun toCredential(
        token: OAuthHttpClient.TokenResponse,
        previous: OAuthCredential?,
        now: Long = System.currentTimeMillis(),
    ): OAuthCredential {
        val idClaims = token.idToken?.let(::decodeJwtPayload)
        val accessClaims = decodeJwtPayload(token.accessToken)
        val authClaims = (idClaims ?: accessClaims)?.get(AUTH_CLAIM) as? JsonObject
        return OAuthCredential(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: previous?.refreshToken,
            expiresAt = token.expiresAtMillis(accessClaims, now),
            accountId = authClaims?.claimString("chatgpt_account_id") ?: previous?.accountId,
            email = idClaims?.claimString("email")
                ?: (idClaims?.get(PROFILE_CLAIM) as? JsonObject)?.claimString("email")
                ?: previous?.email,
            plan = authClaims?.claimString("chatgpt_plan_type") ?: previous?.plan,
        )
    }

    private const val AUTH_CLAIM = "https://api.openai.com/auth"
    private const val PROFILE_CLAIM = "https://api.openai.com/profile"

    /**
     * `chatgpt_plan_type` 的显示名，照搬 codex-rs/protocol 的 `KnownPlan::display_name`，
     * 注意 prolite 才是 "Pro"，pro 是 "Pro (More)"。不认识的值按单词首字母大写显示。
     */
    fun planDisplayName(plan: String): String = when (plan.lowercase()) {
        "free" -> "Free"
        "go" -> "Go"
        "plus" -> "Plus"
        "pro" -> "Pro (More)"
        "prolite" -> "Pro"
        "promax" -> "Pro (Max)"
        "team" -> "Team"
        "self_serve_business_prolite" -> "Self Serve Business ProLite"
        "self_serve_business_usage_based" -> "Self Serve Business Usage Based"
        "business" -> "Business"
        "ent26", "enterprise", "hc" -> "Enterprise"
        "enterprise_cbp_automation" -> "Enterprise (Automation)"
        "enterprise_cbp_usage_based" -> "Enterprise CBP Usage Based"
        "edu", "education" -> "Edu"
        "edu_plus" -> "Edu Plus"
        "edu_pro" -> "Edu Pro"
        else -> plan.split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
