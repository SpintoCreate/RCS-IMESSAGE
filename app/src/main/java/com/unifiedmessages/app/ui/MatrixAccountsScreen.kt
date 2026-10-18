package com.unifiedmessages.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.components.StyleSurface
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.viewmodel.MatrixAccountUiState
import com.unifiedmessages.app.BuildConfig

@Composable
fun MatrixAccountsScreen(
    state: MatrixAccountUiState,
    onDiscover: (String) -> Unit,
    onNetworkTest: () -> Unit,
    onPasswordLogin: (String, String, String) -> Unit,
    onSsoLogin: (String) -> Unit,
    onOidcLogin: (String) -> Unit,
    onLogout: (String) -> Unit,
    onRecoverKeys: (String, String) -> Unit,
    onSetBridgeBot: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var homeserver by rememberSaveable { mutableStateOf("matrix.org") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var recoveryKey by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.padding(top = 12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = theme.onSurface)
            }
            Text("Matrix accounts", style = MaterialTheme.typography.headlineMedium,
                color = theme.onSurface, modifier = Modifier.padding(start = 4.dp, top = 7.dp))
        }
        Text("Sign in to a Matrix homeserver to import rooms on this phone.",
            color = theme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        state.startupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onNetworkTest, enabled = !state.busy) {
                Text("Test Matrix connection")
            }
            state.networkDiagnostics?.let { Text(it, color = theme.onSurfaceVariant) }
        }
        state.accounts.forEach { account ->
            var bridgeBot by rememberSaveable(account.id) { mutableStateOf(account.bridgeBotId.orEmpty()) }
            StyleSurface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column {
                    Text(account.remoteSelfId ?: account.id, color = theme.onSurface)
                    Text(account.connectionState.lowercase().replaceFirstChar { it.titlecase() },
                        color = theme.onSurfaceVariant)
                    if (account.connectionState == "AUTH_EXPIRED") {
                        Text("Your Matrix sign-in expired. Sign in again with this homeserver and account.",
                            color = theme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (account.connectionState == "CONNECTED") {
                        val security = state.security[account.id]
                        if (security?.verification == "UNVERIFIED") {
                            Text("This device is not verified. Verify it from a trusted Matrix device before trusting encrypted chats.",
                                color = theme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (security?.recovery == "ENABLED" || security?.recovery == "INCOMPLETE") {
                            Text("Can't read older encrypted messages? Enter your Matrix recovery key to restore them.",
                                color = theme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(value = recoveryKey, onValueChange = { recoveryKey = it },
                                label = { Text("Recovery key") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                onRecoverKeys(account.id, recoveryKey)
                                recoveryKey = ""
                            }, enabled = !state.busy && recoveryKey.isNotBlank()) {
                                Text("Restore encrypted messages")
                            }
                        }
                    }
                    if (account.providerId == "matrix") {
                        Text("Optional bridge bot: rooms are labeled by another network only when their bridge state matches this Matrix ID.",
                            color = theme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = bridgeBot, onValueChange = { bridgeBot = it },
                            label = { Text("Bridge bot Matrix ID") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { onSetBridgeBot(account.id, bridgeBot) },
                            enabled = !state.busy && bridgeBot.trim() != account.bridgeBotId.orEmpty()) {
                            Text("Save bridge bot")
                        }
                    }
                    if (account.credentialRef != null) {
                        TextButton(onClick = { onLogout(account.id) }, enabled = !state.busy) {
                            Text("Sign out")
                        }
                    }
                }
            }
        }
        OutlinedTextField(value = homeserver, onValueChange = { homeserver = it },
            label = { Text("Homeserver") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onDiscover(homeserver) }, enabled = !state.busy && homeserver.isNotBlank()) {
            Text("Check sign-in methods")
        }
        state.methods?.let { methods ->
            if (methods.password) {
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("Matrix username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    onPasswordLogin(homeserver, username, password)
                    password = ""
                }, enabled = !state.busy && username.isNotBlank() && password.isNotBlank()) {
                    Text("Sign in")
                }
            }
            if (methods.sso || methods.oidc) {
                if (methods.sso) {
                    Button(onClick = { onSsoLogin(homeserver) }, enabled = !state.busy) {
                        Text("Sign in with browser")
                    }
                }
                if (methods.oidc) {
                    Button(onClick = { onOidcLogin(homeserver) }, enabled = !state.busy) {
                        Text("Sign in with OIDC")
                    }
                }
            }
            if (!methods.password && !methods.sso && !methods.oidc) {
                Text("This homeserver did not report a supported sign-in method.", color = theme.onSurfaceVariant)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
