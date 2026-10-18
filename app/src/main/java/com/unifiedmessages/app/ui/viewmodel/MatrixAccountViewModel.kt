package com.unifiedmessages.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedmessages.app.data.local.ProviderAccountEntity
import com.unifiedmessages.app.matrix.MatrixLoginMethods
import com.unifiedmessages.app.matrix.MatrixProvider
import com.unifiedmessages.app.matrix.MatrixPlatformStartup
import com.unifiedmessages.app.matrix.MatrixErrorDiagnostics
import com.unifiedmessages.app.matrix.MatrixSecurityStatus
import com.unifiedmessages.app.matrix.MatrixSyncProgress
import com.unifiedmessages.app.matrix.MatrixSyncPhase
import com.unifiedmessages.app.matrix.MatrixAppVisibility
import org.matrix.rustcomponents.sdk.SyncServiceState
import android.util.Log
import com.unifiedmessages.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    val syncProgress: Map<String, MatrixSyncProgress> = emptyMap(),
)

@HiltViewModel
class MatrixAccountViewModel @Inject constructor(
    private val provider: MatrixProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MatrixAccountUiState())
    val state: StateFlow<MatrixAccountUiState> = mutableState.asStateFlow()
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private var foregroundSyncJob: Job? = null

    init {
        viewModelScope.launch {
            provider.foregroundSyncStates.collectLatest { (accountId, serviceState) ->
                if (serviceState == SyncServiceState.ERROR &&
                    state.value.accounts.any { it.id == accountId && it.connectionState == "CONNECTED" }) {
                    runCatching { provider.stopForegroundSync(accountId) }
                    updateSync(accountId, MatrixSyncProgress.failed(
                        "The SDK sync service stopped. Check your connection and tap Retry."))
                    Log.e("MatrixDebug", "Foreground SDK sync service entered ERROR")
                }
            }
        }
        viewModelScope.launch {
            provider.accounts().collectLatest { accounts ->
                mutableState.value = mutableState.value.copy(accounts = accounts)
                syncRequests.trySend(Unit)
            }
        }
        viewModelScope.launch {
            try { provider.restoreAccounts() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError(error) }
            syncRequests.trySend(Unit)
        }
    }

    /** Called from the activity lifecycle; WorkManager alone handles background catch-up. */
    fun setForegroundActive(active: Boolean) {
        MatrixAppVisibility.isForeground = active
        if (active) {
            if (foregroundSyncJob?.isActive == true) return
            foregroundSyncJob = viewModelScope.launch {
                syncRequests.trySend(Unit)
                while (isActive) {
                    syncRequests.receive()
                    state.value.accounts.filter { it.connectionState == "CONNECTED" &&
                        state.value.syncProgress[it.id]?.phase != MatrixSyncPhase.FAILED }
                        .forEach { runForegroundSync(it.id) }
                    withTimeoutOrNull(30_000) { syncRequests.receive() }
                    syncRequests.trySend(Unit)
                }
            }
        } else {
            foregroundSyncJob?.cancel()
            foregroundSyncJob = null
            viewModelScope.launch {
                state.value.accounts.forEach { account ->
                    runCatching { provider.stopForegroundSync(account.id) }
                }
            }
        }
    }

    private suspend fun runForegroundSync(accountId: String) {
        if (state.value.syncProgress[accountId]?.phase == null ||
            state.value.syncProgress[accountId]?.phase == MatrixSyncPhase.FAILED) {
            updateSync(accountId, MatrixSyncProgress.syncing())
        }
        Log.i("MatrixDebug", "Foreground Matrix sync started")
        try {
            withTimeout(60_000) { provider.syncOnce(accountId) }
            val rooms = provider.roomCount(accountId)
            updateSync(accountId, MatrixSyncProgress.synced(rooms))
            Log.i("MatrixDebug", "Foreground Matrix sync completed; rooms=$rooms")
            try { provider.startForegroundSync(accountId) }
            catch (error: Exception) {
                Log.w("MatrixDebug", "SDK sync service unavailable (${error.javaClass.simpleName}); room refresh continues")
            }
            val status = provider.securityStatus(accountId)
            mutableState.value = state.value.copy(security = state.value.security + (accountId to status))
        } catch (timeout: TimeoutCancellationException) {
            updateSync(accountId, MatrixSyncProgress.failed("Timed out after 60 seconds. Tap Retry."))
            Log.e("MatrixDebug", "Foreground Matrix sync timed out after 60 seconds")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val readable = "${MatrixErrorDiagnostics.explanation(error)} ${MatrixErrorDiagnostics.format(error)}"
            updateSync(accountId, MatrixSyncProgress.failed(readable))
            Log.e("MatrixDebug", "Foreground Matrix sync failed: ${error.javaClass.simpleName}")
        }
    }

    private fun updateSync(accountId: String, progress: MatrixSyncProgress) {
        mutableState.value = state.value.copy(syncProgress = state.value.syncProgress + (accountId to progress))
    }

    fun retrySync(accountId: String) = viewModelScope.launch {
        updateSync(accountId, MatrixSyncProgress.syncing())
        Log.i("MatrixDebug", "Manual Matrix sync retry requested")
        try {
            provider.retryStartup(accountId)
            syncRequests.trySend(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            updateSync(accountId, MatrixSyncProgress.failed(MatrixErrorDiagnostics.format(error)))
            Log.e("MatrixDebug", "Matrix sync retry unavailable: ${error.javaClass.simpleName}")
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
            mutableState.value = state.value.copy(busy = false,
                syncProgress = state.value.syncProgress - accountId)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(error) }
    }

    fun resetLocalMatrixData(accountId: String) = viewModelScope.launch {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            provider.resetLocalMatrixData(accountId)
            mutableState.value = state.value.copy(busy = false,
                security = state.value.security - accountId,
                syncProgress = state.value.syncProgress - accountId)
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
