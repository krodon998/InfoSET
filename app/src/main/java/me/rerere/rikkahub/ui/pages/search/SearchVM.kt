package me.rerere.rikkahub.ui.pages.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.search.ArchiveBrowseKind
import me.rerere.rikkahub.data.search.ArchiveBrowseResult
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import kotlin.uuid.Uuid

private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"

enum class MessageSearchScope {
    CURRENT_ASSISTANT,
    ALL_ASSISTANTS,
}

enum class ArchiveSearchMode {
    TEXT,
    DATE,
    IMAGE,
    VIDEO,
    FILE,
    LINK,
}

private data class SearchRequest(
    val query: String,
    val sort: MessageSearchSort,
    val scope: MessageSearchScope,
    val assistantId: Uuid?,
    val debounce: Boolean,
)

class SearchVM(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val searchRequests = Channel<SearchRequest>(Channel.CONFLATED)
    private var currentAssistantId: Uuid? = null
    private var browseJob: Job? = null

    var searchQuery by mutableStateOf("")
        private set
    var searchMode by mutableStateOf(ArchiveSearchMode.TEXT)
        private set
    var selectedDate by mutableStateOf(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
        private set
    var searchScope by mutableStateOf(MessageSearchScope.CURRENT_ASSISTANT)
        private set
    var sortOrder by mutableStateOf(
        runCatching {
            MessageSearchSort.valueOf(
                context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.RELEVANCE.name)!!
            )
        }.getOrDefault(MessageSearchSort.RELEVANCE)
    )
        private set
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var archiveResults by mutableStateOf<List<ArchiveBrowseResult>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set
    var rebuildProgress by mutableStateOf(0 to 0)
        private set

    init {
        viewModelScope.launch {
            searchRequests.receiveAsFlow().collectLatest { request -> performSearch(request) }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.getCurrentAssistant().id }
                .distinctUntilChanged()
                .collect { assistantId ->
                    currentAssistantId = assistantId
                    if (searchScope == MessageSearchScope.CURRENT_ASSISTANT) {
                        search()
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        searchQuery = query
        if (searchMode == ArchiveSearchMode.TEXT) requestSearch(debounce = true)
    }

    fun onModeChange(mode: ArchiveSearchMode) {
        if (searchMode == mode) return
        searchMode = mode
        results = emptyList()
        archiveResults = emptyList()
        search()
    }

    fun onDateChange(date: LocalDate) {
        selectedDate = date
        if (searchMode == ArchiveSearchMode.DATE) requestBrowse()
    }

    fun onScopeChange(scope: MessageSearchScope) {
        if (searchScope == scope) return
        searchScope = scope
        search()
    }

    fun onSortChange(sort: MessageSearchSort) {
        if (sortOrder == sort) return
        sortOrder = sort
        context.writeStringPreference(SORT_ORDER_PREF_KEY, sort.name)
        if (searchMode == ArchiveSearchMode.TEXT) search()
    }

    fun search() {
        if (searchMode == ArchiveSearchMode.TEXT) {
            requestSearch()
        } else {
            requestBrowse()
        }
    }

    private fun currentScopeAssistant(): Uuid? = when (searchScope) {
        MessageSearchScope.CURRENT_ASSISTANT -> currentAssistantId
        MessageSearchScope.ALL_ASSISTANTS -> null
    }

    private fun requestSearch(debounce: Boolean = false) {
        browseJob?.cancel()
        searchRequests.trySend(
            SearchRequest(
                query = searchQuery,
                sort = sortOrder,
                scope = searchScope,
                assistantId = currentScopeAssistant(),
                debounce = debounce,
            )
        )
    }

    private fun requestBrowse() {
        browseJob?.cancel()
        val assistantId = currentScopeAssistant()
        if (searchScope == MessageSearchScope.CURRENT_ASSISTANT && assistantId == null) {
            archiveResults = emptyList()
            return
        }
        browseJob = viewModelScope.launch {
            isLoading = true
            try {
                val kind = when (searchMode) {
                    ArchiveSearchMode.DATE -> ArchiveBrowseKind.MESSAGE
                    ArchiveSearchMode.IMAGE -> ArchiveBrowseKind.IMAGE
                    ArchiveSearchMode.VIDEO -> ArchiveBrowseKind.VIDEO
                    ArchiveSearchMode.FILE -> ArchiveBrowseKind.FILE
                    ArchiveSearchMode.LINK -> ArchiveBrowseKind.LINK
                    ArchiveSearchMode.TEXT -> return@launch
                }
                archiveResults = conversationRepo.browseArchive(
                    kind = kind,
                    date = selectedDate.takeIf { searchMode == ArchiveSearchMode.DATE },
                    assistantId = assistantId,
                )
            } finally {
                isLoading = false
            }
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            isRebuilding = true
            rebuildProgress = 0 to 0
            try {
                conversationRepo.rebuildAllIndexes { current, total ->
                    rebuildProgress = current to total
                }
            } finally {
                isRebuilding = false
            }
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        results = emptyList()
        if (request.query.isBlank() ||
            (request.scope == MessageSearchScope.CURRENT_ASSISTANT && request.assistantId == null)
        ) {
            return
        }
        isLoading = true
        try {
            if (request.debounce) delay(300L)
            results = conversationRepo.searchMessages(request.query, request.sort, request.assistantId)
        } finally {
            isLoading = false
        }
    }
}
