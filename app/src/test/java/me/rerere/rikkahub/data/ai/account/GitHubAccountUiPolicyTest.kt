package me.rerere.rikkahub.data.ai.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAccountUiPolicyTest {
    @Test
    fun `disconnected failed authorizing and connected expose distinct actions`() {
        assertTrue(GitHubAccountStatus.Disconnected.availableActions().canStartLogin)
        assertTrue(GitHubAccountStatus.Failed("network").availableActions().canStartLogin)
        assertTrue(GitHubAccountStatus.Authorizing.availableActions().canCancelLogin)
        assertTrue(GitHubAccountStatus.DeviceCode("ABCD", "https://github.com/login/device").availableActions().canCancelLogin)

        val connected = GitHubAccountStatus.Connected(1, "daddy", null, null, setOf("repo"))
        assertTrue(connected.availableActions().canLogout)
        assertTrue(connected.availableActions().canReconnect)
        assertFalse(connected.availableActions().canStartLogin)
    }

    @Test
    fun `unavailable state cannot begin a flow without configured client id`() {
        val actions = GitHubAccountStatus.Unavailable("missing client id").availableActions()
        assertFalse(actions.canStartLogin)
        assertFalse(actions.canCancelLogin)
        assertFalse(actions.canLogout)
    }
}
