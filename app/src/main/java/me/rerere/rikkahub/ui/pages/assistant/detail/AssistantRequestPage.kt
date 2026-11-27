package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.SUPPORTED_CONTEXT_TOKEN_BUDGET_OVERRIDES
import me.rerere.rikkahub.data.model.withContextTokenBudgetOverride
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantRequestPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_advanced_settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantRequestContent(
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
internal fun AssistantRequestContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup(
            title = { Text(stringResource(R.string.assistant_context_budget_advanced)) },
        ) {
            item(
                onClick = { onUpdate(assistant.withContextTokenBudgetOverride(null)) },
                headlineContent = { Text(stringResource(R.string.auto)) },
                supportingContent = {
                    Text(stringResource(R.string.assistant_context_budget_auto_desc))
                },
                trailingContent = {
                    RadioButton(
                        selected = assistant.contextTokenBudgetOverride == null,
                        onClick = null,
                    )
                },
            )
            SUPPORTED_CONTEXT_TOKEN_BUDGET_OVERRIDES.forEach { budget ->
                item(
                    onClick = { onUpdate(assistant.withContextTokenBudgetOverride(budget)) },
                    headlineContent = { Text("${budget / 1000}K") },
                    trailingContent = {
                        RadioButton(
                            selected = assistant.contextTokenBudgetOverride == budget,
                            onClick = null,
                        )
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.assistant_legacy_request_overrides),
        )
        CustomHeaders(
            headers = assistant.customHeaders,
            onUpdate = {
                onUpdate(
                    assistant.copy(
                        customHeaders = it
                    )
                )
            }
        )

        CustomBodies(
            customBodies = assistant.customBodies,
            onUpdate = {
                onUpdate(
                    assistant.copy(
                        customBodies = it
                    )
                )
            }
        )
    }
}
