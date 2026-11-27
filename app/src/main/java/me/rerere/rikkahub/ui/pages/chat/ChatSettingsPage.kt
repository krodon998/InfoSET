package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun ChatSettingsPage(assistantId: String) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val assistant = runCatching { Uuid.parse(assistantId) }.getOrNull()
        ?.let(settings::getAssistantById)
        ?: settings.getCurrentAssistant()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("聊天设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.AssistantDetail(assistant.id.toString())) },
                        leadingContent = {
                            UIAvatar(
                                name = assistant.name,
                                value = assistant.avatar,
                                modifier = Modifier.size(52.dp),
                            )
                        },
                        supportingContent = { Text("Aire 与智能") },
                        trailingContent = { TrailingArrow() },
                        headlineContent = {
                            Text(assistant.name.ifBlank { "Aire" }, style = MaterialTheme.typography.titleMedium)
                        },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.MessageSearch) },
                        leadingContent = { CardGroupIcon(HugeIcons.Search01) },
                        trailingContent = { TrailingArrow() },
                        headlineContent = { Text("查找聊天记录") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Favorite) },
                        leadingContent = { CardGroupIcon(HugeIcons.InLove) },
                        trailingContent = { TrailingArrow() },
                        headlineContent = { Text("收藏的消息") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Stats) },
                        leadingContent = { CardGroupIcon(HugeIcons.ChartColumn) },
                        trailingContent = { TrailingArrow() },
                        supportingContent = { Text("请求、Token、缓存与费用") },
                        headlineContent = { Text("仪表盘") },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.AssistantBasic(assistant.id.toString())) },
                        leadingContent = { CardGroupIcon(HugeIcons.Image02) },
                        trailingContent = { TrailingArrow() },
                        headlineContent = { Text("设置此界面背景") },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailingArrow() {
    Column(
        modifier = Modifier.size(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
