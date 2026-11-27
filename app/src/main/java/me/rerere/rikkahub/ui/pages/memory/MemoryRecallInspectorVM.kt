package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.MemoryRecallCandidateEntity
import me.rerere.rikkahub.data.db.entity.MemoryRecallTraceEntity
import me.rerere.rikkahub.data.model.MemoryRecallFeedback
import me.rerere.rikkahub.data.repository.AtomicMemoryRepository

data class RecallInspection(
    val trace: MemoryRecallTraceEntity,
    val candidates: List<MemoryRecallCandidateEntity>,
)

class MemoryRecallInspectorVM(
    private val memoryId: String,
    private val repository: AtomicMemoryRepository,
) : ViewModel() {
    val recentTraces = repository.observeRecentTraces(memoryId)

    private val _inspection = MutableStateFlow<RecallInspection?>(null)
    val inspection = _inspection.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    fun simulate(query: String, tokenBudget: Int = 4_000) {
        if (query.isBlank() || _running.value) return
        viewModelScope.launch {
            _running.value = true
            try {
                val result = repository.activeRecall(
                    memoryId = memoryId,
                    query = query,
                    tokenBudget = tokenBudget,
                    destination = "RECALL_INSPECTOR",
                )
                val trace = recentTrace(result.traceId) ?: return@launch
                _inspection.value = RecallInspection(trace, repository.candidates(trace.id))
            } finally {
                _running.value = false
            }
        }
    }

    fun inspect(trace: MemoryRecallTraceEntity) {
        viewModelScope.launch {
            _inspection.value = RecallInspection(trace, repository.candidates(trace.id))
        }
    }

    fun feedback(feedback: MemoryRecallFeedback) {
        val trace = _inspection.value?.trace ?: return
        viewModelScope.launch {
            repository.recordFeedback(trace.id, feedback)
            _inspection.value = _inspection.value?.copy(trace = trace.copy(feedback = feedback.name))
        }
    }

    private suspend fun recentTrace(id: String): MemoryRecallTraceEntity? = repository.trace(id)
}
