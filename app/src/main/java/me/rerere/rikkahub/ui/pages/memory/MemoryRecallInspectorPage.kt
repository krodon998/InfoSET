package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.model.MemoryRecallFeedback
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

@Composable
fun MemoryRecallInspectorPage(memoryId: String) {
    val vm: MemoryRecallInspectorVM = koinViewModel { parametersOf(memoryId) }
    val recent by vm.recentTraces.collectAsStateWithLifecycle(emptyList())
    val inspection by vm.inspection.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.memory_recall_inspector)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.memory_recall_test_cue)) },
                minLines = 2,
            )
            Button(
                onClick = { vm.simulate(query) },
                enabled = query.isNotBlank() && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (running) R.string.memory_recall_running else R.string.memory_recall_run))
            }

            inspection?.let { selected ->
                CardGroup(title = { Text(stringResource(R.string.memory_recall_injected)) }) {
                    item(
                        overlineContent = {
                            Text("${selected.trace.destination} · ${selected.trace.injectedTokens}/${selected.trace.tokenBudget} tokens")
                        },
                        headlineContent = {
                            Text(
                                selected.trace.injectedContent.ifBlank {
                                    stringResource(R.string.memory_recall_nothing_injected)
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.memory_recall_feedback)) },
                        supportingContent = { Text(selected.trace.feedback ?: stringResource(R.string.memory_recall_no_feedback)) },
                        trailingContent = {
                            Column {
                                TextButton(onClick = { vm.feedback(MemoryRecallFeedback.HELPFUL) }) {
                                    Text(stringResource(R.string.memory_recall_helpful))
                                }
                                TextButton(onClick = { vm.feedback(MemoryRecallFeedback.NOT_HELPFUL) }) {
                                    Text(stringResource(R.string.memory_recall_not_helpful))
                                }
                            }
                        },
                    )
                }
                selected.candidates.forEach { candidate -> CandidateCard(candidate) }
            }

            if (recent.isNotEmpty()) {
                CardGroup(title = { Text(stringResource(R.string.memory_recall_recent)) }) {
                    recent.forEach { trace ->
                        item(
                            onClick = { vm.inspect(trace) },
                            overlineContent = { Text("${trace.destination} · ${trace.injectedTokens} tokens") },
                            headlineContent = { Text(trace.query, maxLines = 2) },
                            supportingContent = { Text(trace.feedback ?: stringResource(R.string.memory_recall_no_feedback)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: MemoryRecallCandidateEntity) {
    CardGroup(title = { Text("#${candidate.rank} · ${candidate.totalScore.score()}") }) {
        item(
            overlineContent = {
                Text(
                    if (candidate.injected) {
                        stringResource(R.string.memory_recall_selected)
                    } else {
                        candidate.filteredReason ?: stringResource(R.string.memory_recall_filtered)
                    }
                )
            },
            headlineContent = { Text(candidate.statement) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        candidate.sourceSummary,
                        "lexical ${candidate.lexicalScore.score()}",
                        "activation ${candidate.activationScore.score()}",
                        "importance ${candidate.importanceScore.score()}",
                        "${candidate.tokenCost} tokens",
                    ).joinToString(" · ")
                )
            },
        )
    }
}

private fun Double.score() = String.format(Locale.ROOT, "%.3f", this)
