package me.rerere.rikkahub.ui.pages.search

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.search.ArchiveBrowseResult
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SearchPage(vm: SearchVM = koinViewModel()) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var showRebuildDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(vm.searchMode) {
        if (vm.searchMode == ArchiveSearchMode.TEXT) focusRequester.requestFocus()
    }

    if (showRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.search_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDialog = false
                        vm.rebuildIndex()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.search_page_title)) },
                actions = {
                    if (vm.searchMode == ArchiveSearchMode.TEXT) {
                        SortMenuButton(
                            current = vm.sortOrder,
                            onSortChange = { vm.onSortChange(it) },
                        )
                        IconButton(
                            onClick = { showRebuildDialog = true },
                            enabled = !vm.isRebuilding,
                        ) {
                            Icon(
                                HugeIcons.Refresh01,
                                contentDescription = stringResource(R.string.search_page_rebuild_button)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ArchiveSearchMode.entries) { mode ->
                    FilterChip(
                        selected = vm.searchMode == mode,
                        onClick = { vm.onModeChange(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }

            if (vm.searchMode == ArchiveSearchMode.TEXT) {
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_page_placeholder)) },
                    shape = RoundedCornerShape(50),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                )
            } else if (vm.searchMode == ArchiveSearchMode.DATE) {
                TextButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                vm.onDateChange(LocalDate(year, month + 1, day))
                            },
                            vm.selectedDate.year,
                            vm.selectedDate.monthNumber - 1,
                            vm.selectedDate.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.search_page_date_jump, vm.selectedDate.toString()))
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            ) {
                MessageSearchScope.entries.forEachIndexed { index, scope ->
                    SegmentedButton(
                        selected = vm.searchScope == scope,
                        onClick = { vm.onScopeChange(scope) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MessageSearchScope.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (scope) {
                                    MessageSearchScope.CURRENT_ASSISTANT -> R.string.search_page_scope_current_assistant
                                    MessageSearchScope.ALL_ASSISTANTS -> R.string.search_page_scope_all_assistants
                                }
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (vm.isLoading || vm.isRebuilding) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    vm.isRebuilding -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val (current, total) = vm.rebuildProgress
                            Text(
                                text = if (total > 0) stringResource(
                                    R.string.search_page_rebuilding,
                                    current,
                                    total
                                ) else stringResource(R.string.search_page_rebuilding_simple),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    vm.searchMode == ArchiveSearchMode.TEXT && vm.searchQuery.isBlank() -> {
                        EmptySearchText(stringResource(R.string.search_page_hint))
                    }

                    vm.searchMode == ArchiveSearchMode.TEXT && vm.results.isEmpty() && !vm.isLoading -> {
                        EmptySearchText(stringResource(R.string.search_page_no_results))
                    }

                    vm.searchMode != ArchiveSearchMode.TEXT && vm.archiveResults.isEmpty() && !vm.isLoading -> {
                        EmptySearchText(stringResource(R.string.search_page_no_results))
                    }

                    vm.searchMode == ArchiveSearchMode.TEXT -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(vm.results, key = { "${it.conversationId}:${it.messageId}" }) { result ->
                                SearchResultItem(
                                    result = result,
                                    onClick = {
                                        navigateToChatPage(
                                            navController,
                                            chatId = Uuid.parse(result.conversationId),
                                            nodeId = Uuid.parse(result.nodeId),
                                            messageId = Uuid.parse(result.messageId),
                                            partId = result.partId?.let { Uuid.parse(it) },
                                        )
                                    }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                vm.archiveResults,
                                key = { "${it.conversationId}:${it.messageId}:${it.partId}:${it.preview}" },
                            ) { result ->
                                ArchiveResultItem(
                                    result = result,
                                    onClick = {
                                        navigateToChatPage(
                                            navController,
                                            chatId = Uuid.parse(result.conversationId),
                                            nodeId = Uuid.parse(result.nodeId),
                                            messageId = Uuid.parse(result.messageId),
                                            partId = result.partId?.let { Uuid.parse(it) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ArchiveSearchMode.labelRes(): Int = when (this) {
    ArchiveSearchMode.TEXT -> R.string.search_page_mode_text
    ArchiveSearchMode.DATE -> R.string.search_page_mode_date
    ArchiveSearchMode.IMAGE -> R.string.search_page_mode_image
    ArchiveSearchMode.VIDEO -> R.string.search_page_mode_video
    ArchiveSearchMode.FILE -> R.string.search_page_mode_file
    ArchiveSearchMode.LINK -> R.string.search_page_mode_link
}

@Composable
private fun SortMenuButton(
    current: MessageSearchSort,
    onSortChange: (MessageSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                HugeIcons.Sorting01,
                contentDescription = stringResource(R.string.search_page_sort)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MessageSearchSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (sort) {
                                    MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                                    MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                                    MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = sort == current,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.search_page_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = result.snippet
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start == -1) {
                append(snippet.substring(index))
                break
            }
            if (start > index) append(snippet.substring(index, start))
            val end = snippet.indexOf(']', start + 1)
            if (end == -1) {
                append(snippet.substring(start))
                break
            }
            withStyle(SpanStyle(background = highlightColor)) {
                append(snippet.substring(start + 1, end))
            }
            index = end + 1
        }
    }

    SearchCard(
        title = result.title.ifBlank { untitled },
        preview = snippetText.toString(),
        time = result.updateAt.toLocalDateTime(),
        onClick = onClick,
    )
}

@Composable
private fun ArchiveResultItem(
    result: ArchiveBrowseResult,
    onClick: () -> Unit,
) {
    SearchCard(
        title = result.title.ifBlank { stringResource(R.string.search_page_untitled) },
        preview = result.preview,
        time = result.createdAt.toString().replace('T', ' '),
        onClick = onClick,
    )
}

@Composable
private fun SearchCard(
    title: String,
    preview: String,
    time: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = CustomColors.listItemColors.containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
