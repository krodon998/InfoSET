package me.rerere.rikkahub.ui.context

import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    @Test
    fun replaceAppSurfaceKeepsLockScreenAndDesktop() {
        val stack = mutableListOf<NavKey>(
            Screen.LockScreen,
            Screen.Desktop,
            Screen.Chat("first"),
            Screen.History,
        )

        Navigator(stack).replaceAppSurface(Screen.Chat("second"))

        assertEquals(
            listOf(Screen.LockScreen, Screen.Desktop, Screen.Chat("second")),
            stack,
        )
    }

    @Test
    fun replaceAppSurfaceRetainsLegacyRootBehaviorWithoutDesktop() {
        val stack = mutableListOf<NavKey>(Screen.Setting)

        Navigator(stack).replaceAppSurface(Screen.Chat("chat"))

        assertEquals(listOf(Screen.Chat("chat")), stack)
    }
}
