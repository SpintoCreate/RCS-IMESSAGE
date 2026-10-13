package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.repository.MessagingRepository
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
    val source: MessageSource? = null,
) {
    val unreadCount: Int get() = conversations.sumOf { it.unreadCount }
}

/** Search, source filtering, pinning, and unread badges all reflect Room queries. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: MessagingRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val source = MutableStateFlow<MessageSource?>(null)

    val uiState: StateFlow<InboxUiState> = combine(query, source) { search, filter ->
        search to filter
    }.flatMapLatest { (search, filter) ->
        repository.observeInbox(search, filter).map { conversations ->
            InboxUiState(conversations, search, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun setQuery(value: String) { query.value = value }

    fun setSource(value: MessageSource?) { source.value = value }

    fun setPinned(threadId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(threadId, pinned) }
    }
}
