package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.repository.CachedContact
import com.unifiedmessages.app.data.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository,
) : ViewModel() {
    val contacts: StateFlow<List<CachedContact>> = repository.contacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val mutableHasPermission = MutableStateFlow(repository.hasPermission())
    val hasPermission: StateFlow<Boolean> = mutableHasPermission

    init {
        refresh()
    }

    fun refresh() {
        mutableHasPermission.value = repository.hasPermission()
        if (mutableHasPermission.value) {
            repository.startObserving(viewModelScope)
            viewModelScope.launch { repository.refreshIfPermitted() }
        } else {
            repository.stopObserving()
            viewModelScope.launch { repository.refreshIfPermitted() }
        }
    }

    override fun onCleared() {
        repository.stopObserving()
        super.onCleared()
    }
}
