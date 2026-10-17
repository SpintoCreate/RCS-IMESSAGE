package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.repository.MessagingRepository
import com.unifiedmessages.app.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class ConversationUiState(
    val conversation: Conversation? = null,
    val searchQuery: String = "",
)

/** Tracks the selected Room thread and marks incoming messages read while it is open. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repository: MessagingRepository,
) : ViewModel() {
    private val selectedThreadId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private var readJob: Job? = null
    private var backfillJob: Job? = null

    val uiState: StateFlow<ConversationUiState> = combine(selectedThreadId, searchQuery) { id, query ->
        id to query
    }.flatMapLatest { (id, query) ->
        if (id == null) flowOf(ConversationUiState(searchQuery = query))
        else repository.observeConversation(id, query).map { conversation ->
            ConversationUiState(conversation, query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    fun selectThread(threadId: String?) {
        if (selectedThreadId.value == threadId) return
        selectedThreadId.value = threadId
        searchQuery.value = ""
        backfillJob?.cancel()
        backfillJob = if (threadId == null) null else viewModelScope.launch {
            repository.observeJoinedMatrixRoom(threadId).distinctUntilChanged().filter { it }.collect {
                try { repository.backfillMatrixOnOpen(threadId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Cached history remains visible while offline. */ }
            }
        }
        readJob?.cancel()
        readJob = if (threadId == null) null else viewModelScope.launch {
            repository.observeUnreadCount(threadId).filter { it > 0 }.collect {
                repository.markRead(threadId)
            }
        }
    }

    fun setSearchQuery(value: String) { searchQuery.value = value }
}
