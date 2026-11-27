package me.rerere.rikkahub.data.ai.account

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.AccountCredentialProvider
import me.rerere.ai.provider.OAuthCredential
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.http.await
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "ProviderAccountManager"

// 与 Codex CLI 一致：access token 过期前 5 分钟内就刷新
private const val TOKEN_REFRESH_LEEWAY_MS = 5 * 60_000L
private val AUTHORIZATION_TIMEOUT = 5.minutes

sealed interface AccountLoginStatus {
    data object Authorizing : AccountLoginStatus

    /** 设备码登录：用户在浏览器里打开 [verificationUrl] 确认 [userCode] */
    data class DeviceCode(val userCode: String, val verificationUrl: String) : AccountLoginStatus

    data class Failed(val message: String) : AccountLoginStatus
}

/**
 * 管理账号型提供商（[ProviderSetting.Account]）的登录、token 刷新和凭据持久化。
 *
 * 凭据只由这里写回设置，界面编辑提供商时应保留设置里的最新凭据，见 [preserveCredential]。
 */
class ProviderAccountManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val client: OkHttpClient,
    private val credentialVault: ProviderCredentialVault,
) : AccountCredentialProvider {
    // 走应用的 OkHttpClient，授权和刷新请求也能用上设置里的代理
    private val oauthClient = OAuthHttpClient(client)
    private val chatGPTCallbackServer = OAuthLoopbackCallbackServer(
        port = ChatGPTAuth.CALLBACK_PORT,
        callbackPath = ChatGPTAuth.CALLBACK_PATH,
        redirectHost = ChatGPTAuth.CALLBACK_HOST,
    )
    private val loginJobs = ConcurrentHashMap<Uuid, Job>()
    private val refreshLocks = ConcurrentHashMap<Uuid, Mutex>()

    private val _loginStatus = MutableStateFlow<Map<Uuid, AccountLoginStatus>>(emptyMap())
    val loginStatus: StateFlow<Map<Uuid, AccountLoginStatus>> = _loginStatus.asStateFlow()

    init {
        // One-way migration for releases that stored account tokens in Settings JSON.
        appScope.launch(Dispatchers.IO) { migrateLegacyCredentials() }
    }

    fun startLogin(providerId: Uuid, context: Context) {
        val provider = findAccount(providerId) ?: return
        loginJobs.remove(providerId)?.cancel()
        setStatus(providerId, AccountLoginStatus.Authorizing)
        val job = appScope.launch {
            try {
                when (provider) {
                    is ProviderSetting.ChatGPT -> {
                        val result = loginChatGPT(provider, context.applicationContext)
                        persistCredential(
                            providerId = providerId,
                            credential = result.credential,
                            chatGPTClientId = result.clientId,
                            chatGPTHostId = result.hostId,
                        )
                    }

                    is ProviderSetting.Grok -> persistCredential(
                        providerId,
                        loginGrok(providerId, context.applicationContext),
                    )
                }
                setStatus(providerId, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Login failed for ${provider.name}", e)
                setStatus(
                    providerId,
                    AccountLoginStatus.Failed(e.message ?: e.javaClass.simpleName)
                )
            }
        }
        loginJobs[providerId] = job
        job.invokeOnCompletion { loginJobs.remove(providerId, job) }
    }

    fun cancelLogin(providerId: Uuid) {
        loginJobs.remove(providerId)?.cancel()
        setStatus(providerId, null)
    }

    suspend fun logout(providerId: Uuid) {
        cancelLogin(providerId)
        persistCredential(providerId, null)
    }

    /**
     * 需要刷新时在 [appScope] 里执行，调用方被取消（停止生成、关掉额度界面）时打断不了已经发出的刷新：
     * 服务端一收到请求就会轮换 refresh token，新的没存下来就只能重新登录。
     */
    override suspend fun getCredential(
        setting: ProviderSetting.Account,
        rejectedAccessToken: String?,
    ): OAuthCredential {
        val latest = findAccount(setting.id) ?: setting
        if (latest.credential == null) error("${latest.name}: not signed in")
        // 绝大多数请求的 token 都还有效，直接返回，不用切线程排队拿锁
        credentialVault.read(setting.id)
            ?.takeUnless { it.needsRefresh(rejectedAccessToken) }
            ?.let { return it }
        return appScope.async(Dispatchers.IO) {
            refreshIfNeeded(setting, rejectedAccessToken)
        }.await()
    }

    /**
     * 按提供商串行刷新。拿到锁后重新读设置：并发请求可能已经刷新过，refresh token 只能用一次。
     */
    private suspend fun refreshIfNeeded(
        setting: ProviderSetting.Account,
        rejectedAccessToken: String?,
    ): OAuthCredential {
        val lock = refreshLocks.computeIfAbsent(setting.id) { Mutex() }
        return lock.withLock {
            val latest = findAccount(setting.id) ?: setting
            val credential = credentialVault.read(setting.id)
                ?: migrateLegacyCredential(latest)
                ?: error("${latest.name}: not signed in")
            if (!credential.needsRefresh(rejectedAccessToken)) return@withLock credential

            val refreshToken = credential.refreshToken
                ?: error("${latest.name}: session expired, please sign in again")
            val refreshed = try {
                when (latest) {
                    is ProviderSetting.ChatGPT -> ChatGPTAuth.toCredential(
                        token = oauthClient.refreshToken(
                            ChatGPTAuth.refreshRequest(
                                refreshToken = refreshToken,
                                clientId = latest.oauthClientId
                                    ?: error("${latest.name}: OAuth registration missing, please sign in again"),
                            )
                        ),
                        previous = credential,
                    )

                    is ProviderSetting.Grok -> GrokAuth.toCredential(
                        token = oauthClient.refreshToken(GrokAuth.refreshRequest(refreshToken)),
                        previous = credential,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException(
                    "${latest.name}: failed to refresh sign-in, please sign in again",
                    e
                )
            }
            // 刷新期间用户可能退出或换了账号，这时结果作废，不能把旧会话写回去
            if (!replaceCredential(latest.id, expected = credential, credential = refreshed)) {
                error("${latest.name}: sign-in changed during refresh, please try again")
            }
            refreshed
        }
    }

    private data class ChatGPTLoginResult(
        val credential: OAuthCredential,
        val clientId: String,
        val hostId: String,
    )

    private suspend fun loginChatGPT(
        provider: ProviderSetting.ChatGPT,
        context: Context,
    ): ChatGPTLoginResult =
        withContext(Dispatchers.IO) {
            val pkce = oauthClient.generatePkce()
            val state = oauthClient.generateState()
            val nonce = oauthClient.generateState()
            val hostId = provider.oauthHostId ?: "urn:uuid:${UUID.randomUUID()}"
            val requestedClientId = provider.oauthClientId ?: ChatGPTAuth.DYNAMIC_CLIENT_ID
            val session = chatGPTCallbackServer.openSession(context, state)
            try {
                val authorizationUrl = oauthClient.buildAuthorizationUrl(
                    ChatGPTAuth.authorizationRequest(
                        pkce = pkce,
                        state = state,
                        redirectUri = session.redirectUri,
                        clientId = requestedClientId,
                        hostId = hostId,
                        nonce = nonce,
                    )
                )
                withContext(Dispatchers.Main) {
                    CustomTabsOAuthAuthorizationLauncher.launch(context, authorizationUrl)
                }
                val callback =
                    session.awaitCallback(AUTHORIZATION_TIMEOUT) ?: error("Authorization timed out")
                callback.error?.let { code ->
                    error(listOfNotNull(code, callback.errorDescription).joinToString(": "))
                }
                val code = callback.code ?: error("No authorization code returned")
                val issuedClientId = callback.clientId ?: requestedClientId
                val token = oauthClient.exchangeAuthorizationCode(
                    ChatGPTAuth.tokenRequest(
                        clientId = issuedClientId,
                        code = code,
                        codeVerifier = pkce.verifier,
                        redirectUri = session.redirectUri,
                    )
                )
                ChatGPTAuth.requireValidIdToken(
                    idToken = token.idToken,
                    clientId = issuedClientId,
                    nonce = nonce,
                    nowSeconds = System.currentTimeMillis() / 1_000,
                )
                ChatGPTLoginResult(
                    credential = ChatGPTAuth.toCredential(token, previous = null),
                    clientId = issuedClientId,
                    hostId = hostId,
                )
            } finally {
                withContext(NonCancellable) { session.close() }
            }
        }

    // 登录由 appScope（主线程）发起，网络请求和读响应体都放到 IO
    private suspend fun loginGrok(providerId: Uuid, context: Context): OAuthCredential =
        withContext(Dispatchers.IO) {
            val authorization =
                oauthClient.requestDeviceAuthorization(GrokAuth.deviceAuthorizationRequest())
            // 带 user_code 的地址打开后不用手动输入
            val url = authorization.verificationUriComplete ?: authorization.verificationUri
            require(url.startsWith("https://")) { "Invalid verification URL: $url" }
            setStatus(providerId, AccountLoginStatus.DeviceCode(authorization.userCode, url))
            withContext(Dispatchers.Main) {
                CustomTabsOAuthAuthorizationLauncher.launch(context, url)
            }
            val token = oauthClient.awaitDeviceToken(
                GrokAuth.deviceTokenRequest(authorization.deviceCode),
                authorization
            )
            val credential = GrokAuth.toCredential(token, previous = null)
            if (credential.email != null) credential else credential.copy(email = fetchGrokEmail(credential))
        }

    // token 里没有邮箱时查 userinfo，只用于界面展示，查不到不影响登录
    private suspend fun fetchGrokEmail(credential: OAuthCredential): String? = runCatching {
        val request = Request.Builder()
            .url(GrokAuth.USERINFO_ENDPOINT)
            .header("Authorization", "Bearer ${credential.accessToken}")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use null
            JsonInstant.parseToJsonElement(response.body.string()).jsonObject.claimString("email")
        }
    }.onFailure { Log.w(TAG, "Failed to fetch Grok userinfo", it) }.getOrNull()

    private suspend fun persistCredential(
        providerId: Uuid,
        credential: OAuthCredential?,
        chatGPTClientId: String? = null,
        chatGPTHostId: String? = null,
    ) {
        if (credential == null) credentialVault.remove(providerId) else credentialVault.write(providerId, credential)
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerId && provider is ProviderSetting.Account) {
                        val updated = provider.withCredential(credential?.redacted())
                        if (updated is ProviderSetting.ChatGPT) {
                            updated.copy(
                                oauthClientId = chatGPTClientId ?: updated.oauthClientId,
                                oauthHostId = chatGPTHostId ?: updated.oauthHostId,
                            )
                        } else {
                            updated
                        }
                    } else {
                        provider
                    }
                }
            )
        }
    }

    /** 设置里的凭据仍是 [expected] 时才替换，返回是否替换了 */
    private suspend fun replaceCredential(
        providerId: Uuid,
        expected: OAuthCredential,
        credential: OAuthCredential,
    ): Boolean {
        if (credentialVault.read(providerId) != expected) return false
        credentialVault.write(providerId, credential)
        var replaced = false
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerId && provider is ProviderSetting.Account &&
                        provider.credential != null) {
                        replaced = true
                        provider.withCredential(credential.redacted())
                    } else {
                        provider
                    }
                }
            )
        }
        if (!replaced && credentialVault.read(providerId) == credential) {
            credentialVault.remove(providerId)
        }
        return replaced
    }

    private suspend fun migrateLegacyCredentials() {
        settingsStore.awaitLoaded()
        settingsStore.settingsFlow.value.providers
            .filterIsInstance<ProviderSetting.Account>()
            .forEach { migrateLegacyCredential(it) }
    }

    private suspend fun migrateLegacyCredential(provider: ProviderSetting.Account): OAuthCredential? {
        credentialVault.read(provider.id)?.let { return it }
        val legacy = provider.credential?.takeIf { it.accessToken.isNotBlank() } ?: return null
        credentialVault.write(provider.id, legacy)
        var migrated = false
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map {
                    if (it.id == provider.id && it is ProviderSetting.Account && it.credential == legacy) {
                        migrated = true
                        it.withCredential(legacy.redacted())
                    } else {
                        it
                    }
                }
            )
        }
        if (!migrated && credentialVault.read(provider.id) == legacy) {
            credentialVault.remove(provider.id)
        }
        return legacy.takeIf { migrated }
    }

    private fun OAuthCredential.redacted() = copy(accessToken = "", refreshToken = null)

    // 刚被服务端拒绝，或者快过期了
    private fun OAuthCredential.needsRefresh(rejectedAccessToken: String?): Boolean =
        accessToken == rejectedAccessToken ||
            (expiresAt > 0 && System.currentTimeMillis() >= expiresAt - TOKEN_REFRESH_LEEWAY_MS)

    private fun findAccount(providerId: Uuid): ProviderSetting.Account? =
        settingsStore.settingsFlow.value.providers
            .find { it.id == providerId } as? ProviderSetting.Account

    private fun setStatus(providerId: Uuid, status: AccountLoginStatus?) {
        _loginStatus.update { if (status == null) it - providerId else it + (providerId to status) }
    }
}

/**
 * 界面整份写回提供商时用：凭据只能由 [ProviderAccountManager] 修改，
 * 编辑期间后台可能已经刷新过 token，旧凭据写回会把轮换后的 refresh token 覆盖掉。
 */
fun ProviderSetting.preserveCredential(latest: ProviderSetting): ProviderSetting =
    if (this is ProviderSetting.Account && latest is ProviderSetting.Account) {
        withCredential(latest.credential)
    } else {
        this
    }
