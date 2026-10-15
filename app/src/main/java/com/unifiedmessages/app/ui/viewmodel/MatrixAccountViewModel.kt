package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.local.ProviderAccountEntity
import com.unifiedmessages.app.matrix.MatrixLoginMethods
import com.unifiedmessages.app.matrix.MatrixProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class MatrixAccountUiState(
    val accounts: List<ProviderAccountEntity> = emptyList(),
    val methods: MatrixLoginMethods? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MatrixAccountViewModel @Inject constructor(
    private val provider: MatrixProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MatrixAccountUiState())
    val state: StateFlow<MatrixAccountUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            provider.accounts().collectLatest { accounts ->
                mutableState.value = mutableState.value.copy(accounts = accounts)
            }
        }
        viewModelScope.launch {
            try { provider.restoreAccounts() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError(error) }
            while (true) {
                val connected = state.value.accounts.filter { it.connectionState == "CONNECTED" }
                connected.forEach { account ->
                    try { provider.syncOnce(account.id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { showError(error) }
                }
                delay(if (connected.isEmpty()) 5_000 else 1_000)
            }
        }
    }

    fun discover(homeserver: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, methods = null, error = null)
        try {
            val methods = provider.offeredLoginMethods(homeserver.trim())
            mutableState.value = state.value.copy(methods = methods, busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun loginPassword(homeserver: String, username: String, password: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.loginPassword(homeserver.trim(), username.trim(), password)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun beginSso(homeserver: String, openBrowser: (String) -> Unit) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            val url = provider.beginSso(homeserver)
            openBrowser(url)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun finishSso(callbackUrl: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.finishSso(callbackUrl)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun logout(accountId: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.logout(accountId)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    private fun showError(error: Exception) {
        mutableState.value = state.value.copy(
            busy = false, error = error.message ?: "Matrix connection failed. Try again.",
        )
    }
}
