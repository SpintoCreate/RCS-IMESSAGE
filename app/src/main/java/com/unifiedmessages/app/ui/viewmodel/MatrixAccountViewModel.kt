package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.local.ProviderAccountEntity
import com.unifiedmessages.app.matrix.MatrixLoginMethods
import com.unifiedmessages.app.matrix.MatrixProvider
import com.unifiedmessages.app.matrix.MatrixPlatformStartup
import com.unifiedmessages.app.matrix.MatrixErrorDiagnostics
import com.unifiedmessages.app.matrix.MatrixSecurityStatus
import android.util.Log
import com.unifiedmessages.app.BuildConfig
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
    val security: Map<String, MatrixSecurityStatus> = emptyMap(),
    val methods: MatrixLoginMethods? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val startupError: String? = MatrixPlatformStartup.errorMessage,
    val networkDiagnostics: String? = null,
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
                var hadFailure = false
                connected.forEach { account ->
                    try {
                        provider.syncOnce(account.id)
                        val status = provider.securityStatus(account.id)
                        mutableState.value = state.value.copy(security = state.value.security + (account.id to status))
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { hadFailure = true; showError(error) }
                }
                delay(if (hadFailure) 15_000 else if (connected.isEmpty()) 5_000 else 1_000)
            }
        }
    }

    fun discover(homeserver: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, methods = null, error = null)
        try {
            val methods = provider.offeredLoginMethods(homeserver.trim())
            mutableState.value = state.value.copy(methods = methods, busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, logToLogcat = true) }
    }

    fun diagnoseNetwork() = viewModelScope.launch {
        if (!BuildConfig.DEBUG) return@launch
        mutableState.value = state.value.copy(busy = true, networkDiagnostics = null)
        try {
            val result = provider.diagnoseNetwork()
            mutableState.value = state.value.copy(busy = false, networkDiagnostics = result)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, logToLogcat = true) }
    }

    fun loginPassword(homeserver: String, username: String, password: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.loginPassword(homeserver.trim(), username.trim(), password)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, listOf(password), logToLogcat = true) }
    }

    fun beginSso(homeserver: String, openBrowser: (String) -> Unit) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            val url = provider.beginSso(homeserver)
            openBrowser(url)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, logToLogcat = true) }
    }

    fun beginOidc(homeserver: String, openBrowser: (String) -> Unit) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            val url = provider.beginOidc(homeserver)
            openBrowser(url)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, logToLogcat = true) }
    }

    fun finishBrowserLogin(callbackUrl: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.finishBrowserLogin(callbackUrl)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, listOf(callbackUrl), logToLogcat = true) }
    }

    fun logout(accountId: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.logout(accountId)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun resetLocalMatrixData(accountId: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.resetLocalMatrixData(accountId)
            mutableState.value = state.value.copy(busy = false,
                security = state.value.security - accountId)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun recoverKeys(accountId: String, recoveryKey: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.recoverKeys(accountId, recoveryKey)
            mutableState.value = state.value.copy(busy = false,
                security = state.value.security + (accountId to provider.securityStatus(accountId)))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error, listOf(recoveryKey)) }
    }

    fun setBridgeBot(accountId: String, botId: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.setBridgeBot(accountId, botId)
            mutableState.value = state.value.copy(busy = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    private fun showError(error: Exception, secrets: List<String> = emptyList(), logToLogcat: Boolean = false) {
        val detail = MatrixErrorDiagnostics.format(error, secrets)
        val message = "${MatrixErrorDiagnostics.explanation(error)}\n$detail"
        if (BuildConfig.DEBUG && logToLogcat) {
            message.lineSequence().forEach { line ->
                line.chunked(3_000).forEach { chunk -> Log.e("MatrixDebug", chunk) }
            }
        }
        mutableState.value = state.value.copy(
            busy = false, error = message,
        )
    }
}
