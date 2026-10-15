package com.unifiedmessages.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.provider.ProviderSendRouter
import com.unifiedmessages.app.provider.SendAvailability
import com.unifiedmessages.app.matrix.MatrixProvider
import com.unifiedmessages.app.matrix.MatrixMessageActions
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
    private val matrixProvider: MatrixProvider,
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

    fun reply(threadId: String, targetMessageId: String, body: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try { router.reply(threadId, targetMessageId, body); onComplete(true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableError.value = error.message ?: "Could not send this reply."
                onComplete(false)
            }
        }
    }

    fun clearError() { mutableError.value = null }

    fun loadMatrixActions(messageId: String, onLoaded: (MatrixMessageActions) -> Unit) {
        viewModelScope.launch {
            try { onLoaded(matrixProvider.availableActions(messageId)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = error.message ?: "Message actions are unavailable." }
        }
    }

    fun react(messageId: String, emoji: String) = matrixAction {
        matrixProvider.toggleReaction(messageId, emoji)
    }

    fun edit(messageId: String, body: String) = matrixAction {
        matrixProvider.editMessage(messageId, body)
    }

    fun unsend(messageId: String) = matrixAction {
        matrixProvider.unsendMessage(messageId)
    }

    private fun matrixAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = error.message ?: "Matrix action failed. Try again." }
        }
    }
}
