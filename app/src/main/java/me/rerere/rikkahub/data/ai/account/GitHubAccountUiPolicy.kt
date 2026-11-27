package me.rerere.rikkahub.data.ai.account

data class GitHubAccountActions(
    val canStartLogin: Boolean,
    val canCancelLogin: Boolean,
    val canLogout: Boolean,
    val canReconnect: Boolean,
)

fun GitHubAccountStatus.availableActions(): GitHubAccountActions = when (this) {
    GitHubAccountStatus.Disconnected,
    is GitHubAccountStatus.Failed,
    -> GitHubAccountActions(true, false, false, false)
    GitHubAccountStatus.Authorizing,
    is GitHubAccountStatus.DeviceCode,
    -> GitHubAccountActions(false, true, false, false)
    is GitHubAccountStatus.Connected -> GitHubAccountActions(false, false, true, true)
    GitHubAccountStatus.Loading,
    is GitHubAccountStatus.Unavailable,
    -> GitHubAccountActions(false, false, false, false)
}
