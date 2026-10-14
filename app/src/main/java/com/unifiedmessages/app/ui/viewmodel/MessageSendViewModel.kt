package com.unifiedmessages.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.provider.ProviderSendRouter
import com.unifiedmessages.app.provider.SendAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MessageSendViewModel @Inject constructor(
    private val router: ProviderSendRouter,
) : ViewModel() {
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    fun availability(conversation: Conversation, region: String): SendAvailability =
        router.availability(conversation.route, conversation.recipients.ifEmpty { listOf(conversation.title) }, region)

    fun send(
        threadId: String,
        body: String,
        attachments: List<Uri>,
        showErrorDialog: Boolean = true,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                router.send(threadId, body, attachments)
                onComplete(true, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = error.message ?: "Could not send this message. Try again."
                if (showErrorDialog) mutableError.value = reason
                onComplete(false, reason)
            }
        }
    }

    fun retry(messageId: String) {
        viewModelScope.launch {
            try {
                router.retry(messageId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableError.value = error.message ?: "Could not retry this message."
            }
        }
    }

    fun clearError() { mutableError.value = null }
}
