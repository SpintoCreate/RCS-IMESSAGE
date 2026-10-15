package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.repository.MessagingRepository
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
    val source: MessageSource? = null,
    val isSearching: Boolean = false,
    val availableSources: List<MessageSource> = listOf(MessageSource.SMS),
) {
    val unreadCount: Int get() = conversations.sumOf { it.unreadCount }
}

private data class InboxResults(
    val query: String,
    val source: MessageSource?,
    val conversations: List<Conversation>,
)

/** Text input updates immediately; only database searches wait for a short typing pause. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: MessagingRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val source = MutableStateFlow<MessageSource?>(null)

    private val results: StateFlow<InboxResults?> = combine(
        query.debounce { if (it.isBlank()) 0L else 200L }.map { it.trim() }.distinctUntilChanged(),
        source,
    ) { search, filter -> search to filter }
        .flatMapLatest { (search, filter) ->
            repository.observeInbox(search, filter).map { conversations ->
                InboxResults(search, filter, conversations)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<InboxUiState> = combine(query, source, results,
        repository.observeAvailableSources()) { text, filter, result, availableSources ->
        val current = result?.takeIf { it.query == text.trim() && it.source == filter }
        InboxUiState(
            conversations = current?.conversations.orEmpty(),
            query = text,
            source = filter,
            isSearching = current == null,
            availableSources = availableSources,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState(isSearching = true))

    fun setQuery(value: String) { query.value = value }

    fun setSource(value: MessageSource?) { source.value = value }

    fun setPinned(threadId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(threadId, pinned) }
    }
}
