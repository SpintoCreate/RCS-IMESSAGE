package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.sms.SmsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SmsConnectionUiState(
    val isDefaultApp: Boolean = false,
    val ready: Boolean = false,
    val mmsReady: Boolean = false,
    val importing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SmsConnectionViewModel @Inject constructor(
    private val provider: SmsProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SmsConnectionUiState())
    val uiState: StateFlow<SmsConnectionUiState> = mutableState.asStateFlow()
    private var importJob: Job? = null

    fun refresh() {
        val defaultApp = provider.isDefaultSmsApp()
        val ready = provider.isReady()
        val mmsReady = ready && provider.hasMmsPermissions()
        mutableState.value = mutableState.value.copy(isDefaultApp = defaultApp, ready = ready, mmsReady = mmsReady)
        if (!ready) {
            importJob?.cancel()
            importJob = null
            mutableState.value = mutableState.value.copy(importing = false)
            return
        }
        // A resume scan also catches messages received while another app held the SMS role.
        if (importJob?.isActive != true) {
            importJob = viewModelScope.launch {
                mutableState.value = mutableState.value.copy(importing = true)
                try {
                    provider.importExisting()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    showError(error)
                } finally {
                    mutableState.value = mutableState.value.copy(importing = false)
                }
            }
        }
    }

    fun startThread(address: String, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                onComplete(provider.startThread(address))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error)
                onComplete(null)
            }
        }
    }

    /** Recipient-screen errors stay inline so the address chips and unsent draft remain visible. */
    fun startThread(recipients: List<String>, region: String, onComplete: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                onComplete(provider.startThread(recipients, region), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onComplete(null, error.message ?: "Could not start this conversation. Try again.")
            }
        }
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    private fun showError(error: Exception) {
        mutableState.value = mutableState.value.copy(error = error.message ?: "Messaging failed. Try again.")
    }
}
