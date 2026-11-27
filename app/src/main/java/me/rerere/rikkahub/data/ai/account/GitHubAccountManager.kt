package me.rerere.rikkahub.data.ai.account

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.http.await
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request

private const val GITHUB_TAG = "GitHubAccountManager"

sealed interface GitHubAccountStatus {
    data object Loading : GitHubAccountStatus
    data object Disconnected : GitHubAccountStatus
    data object Authorizing : GitHubAccountStatus
    data class Unavailable(val reason: String) : GitHubAccountStatus
    data class DeviceCode(val userCode: String, val verificationUrl: String) : GitHubAccountStatus
    data class Connected(
        val accountId: Long,
        val login: String,
        val displayName: String?,
        val avatarUrl: String?,
        val grantedScopes: Set<String>,
    ) : GitHubAccountStatus
    data class Failed(val message: String) : GitHubAccountStatus
}

/** Owns the dedicated GitHub login used by Daddy's home tools. */
class GitHubAccountManager(
    private val appScope: AppScope,
    private val httpClient: OkHttpClient,
    private val oauthClient: OAuthHttpClient,
    private val vault: GitHubCredentialVault,
    private val clientId: String,
) {
    private val _status = MutableStateFlow<GitHubAccountStatus>(GitHubAccountStatus.Loading)
    val status: StateFlow<GitHubAccountStatus> = _status.asStateFlow()

    @Volatile
    private var credential: GitHubAccountCredential? = null
    private var loginJob: Job? = null

    init {
        appScope.launch(Dispatchers.IO) {
            if (clientId.isBlank()) {
                _status.value = GitHubAccountStatus.Unavailable("GitHub OAuth client id is not configured")
                return@launch
            }
            runCatching { vault.read() }
                .onSuccess { saved ->
                    credential = saved
                    _status.value = saved?.takeUnless(GitHubAccountCredential::isExpired)
                        ?.toStatus()
                        ?: GitHubAccountStatus.Disconnected
                }
                .onFailure { error ->
                    Log.e(GITHUB_TAG, "Unable to read GitHub credential", error)
                    _status.value = GitHubAccountStatus.Failed("Unable to read the GitHub sign-in")
                }
        }
    }

    fun startLogin(context: Context) {
        if (clientId.isBlank()) {
            _status.value = GitHubAccountStatus.Unavailable("GitHub OAuth client id is not configured")
            return
        }
        loginJob?.cancel()
        _status.value = GitHubAccountStatus.Authorizing
        loginJob = appScope.launch {
            try {
                val connected = login(context.applicationContext)
                withContext(Dispatchers.IO) { vault.write(connected) }
                credential = connected
                _status.value = connected.toStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(GITHUB_TAG, "GitHub sign-in failed", error)
                _status.value = GitHubAccountStatus.Failed(
                    error.message ?: "GitHub sign-in failed"
                )
            }
        }.also { job -> job.invokeOnCompletion { if (loginJob === job) loginJob = null } }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        _status.value = credential?.toStatus() ?: GitHubAccountStatus.Disconnected
    }

    suspend fun logout() {
        cancelLogin()
        withContext(Dispatchers.IO) { vault.clear() }
        credential = null
        _status.value = if (clientId.isBlank()) {
            GitHubAccountStatus.Unavailable("GitHub OAuth client id is not configured")
        } else {
            GitHubAccountStatus.Disconnected
        }
    }

    fun requireCredential(): GitHubAccountCredential =
        credential?.takeUnless(GitHubAccountCredential::isExpired)
            ?: error("GitHub is not signed in")

    /** Runtime-only Authorization header for the official GitHub MCP transport. */
    internal fun authorizationHeader(): Pair<String, String>? =
        credential?.takeUnless(GitHubAccountCredential::isExpired)?.let {
            "Authorization" to "${it.tokenType} ${it.accessToken}"
        }

    private suspend fun login(context: Context): GitHubAccountCredential = withContext(Dispatchers.IO) {
        val authorization = oauthClient.requestDeviceAuthorization(
            GitHubAuth.deviceAuthorizationRequest(clientId)
        )
        val url = authorization.verificationUriComplete ?: authorization.verificationUri
        require(url.startsWith("https://")) { "Invalid GitHub verification URL: $url" }
        _status.value = GitHubAccountStatus.DeviceCode(authorization.userCode, url)
        withContext(Dispatchers.Main) {
            CustomTabsOAuthAuthorizationLauncher.launch(context, url)
        }
        val token = oauthClient.awaitDeviceToken(
            GitHubAuth.deviceTokenRequest(clientId, authorization.deviceCode),
            authorization,
        )
        GitHubAuth.toCredential(token, fetchUser(token))
    }

    private suspend fun fetchUser(token: OAuthHttpClient.TokenResponse): GitHubUser {
        val request = Request.Builder()
            .url(GitHubAuth.USER_ENDPOINT)
            .header("Authorization", "${token.tokenType} ${token.accessToken}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "InfoSET")
            .build()
        return httpClient.newCall(request).await().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "GitHub user lookup failed: HTTP ${response.code}" }
            JsonInstant.decodeFromString(GitHubUser.serializer(), body)
        }
    }

    private fun GitHubAccountCredential.toStatus() = GitHubAccountStatus.Connected(
        accountId = accountId,
        login = login,
        displayName = displayName,
        avatarUrl = avatarUrl,
        grantedScopes = grantedScopes,
    )
}
