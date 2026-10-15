package com.unifiedmessages.app.ui

import android.app.Activity
import android.app.role.RoleManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.unifiedmessages.app.data.AppearanceRepository
import com.unifiedmessages.app.data.repository.DeletedMessagePreferences
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.ui.components.AppBackdrop
import com.unifiedmessages.app.ui.components.AppMessageDialog
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.components.LocalAppHazeState
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import com.unifiedmessages.app.ui.theme.UnifiedMessagesTheme
import com.unifiedmessages.app.ui.viewmodel.ConversationViewModel
import com.unifiedmessages.app.ui.viewmodel.ContactsViewModel
import com.unifiedmessages.app.ui.viewmodel.InboxViewModel
import com.unifiedmessages.app.ui.viewmodel.MessageSendViewModel
import com.unifiedmessages.app.ui.viewmodel.MatrixAccountViewModel
import com.unifiedmessages.app.ui.viewmodel.SmsConnectionViewModel
import com.unifiedmessages.app.sms.PhoneNumbers
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MessageDisplayEntryPoint {
    fun deletedMessagePreferences(): DeletedMessagePreferences
}

@Composable
fun UnifiedMessagesApp(
    sharedAttachments: List<Uri> = emptyList(),
    requestedRecipient: String? = null,
    matrixCallbackUrl: String? = null,
    onShareHandled: () -> Unit = {},
    onRecipientHandled: () -> Unit = {},
    onMatrixCallbackHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val inboxViewModel: InboxViewModel = hiltViewModel()
    val conversationViewModel: ConversationViewModel = hiltViewModel()
    val smsConnectionViewModel: SmsConnectionViewModel = hiltViewModel()
    val messageSendViewModel: MessageSendViewModel = hiltViewModel()
    val matrixAccountViewModel: MatrixAccountViewModel = hiltViewModel()
    val contactsViewModel: ContactsViewModel = hiltViewModel()
    val inboxState by inboxViewModel.uiState.collectAsState()
    val conversationState by conversationViewModel.uiState.collectAsState()
    val smsState by smsConnectionViewModel.uiState.collectAsState()
    val sendError by messageSendViewModel.error.collectAsState()
    val matrixAccountState by matrixAccountViewModel.state.collectAsState()
    val contacts by contactsViewModel.contacts.collectAsState()
    val hasContactsPermission by contactsViewModel.hasPermission.collectAsState()
    val deletedMessagePreferences = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MessageDisplayEntryPoint::class.java,
        ).deletedMessagePreferences()
    }
    val showSenderDeletedMessages by deletedMessagePreferences.showSenderDeletedMessages.collectAsState(initial = true)
    val preferences = remember(context) {
        context.getSharedPreferences("unified_messages_display", Context.MODE_PRIVATE)
    }
    val appearanceRepository = remember(context) { AppearanceRepository(context.applicationContext) }
    val storedAppearance by appearanceRepository.settings.collectAsState(initial = AppearanceSettings())
    var pendingAppearance by remember { mutableStateOf<AppearanceSettings?>(null) }
    val appearance = pendingAppearance ?: storedAppearance
    val scope = rememberCoroutineScope()
    LaunchedEffect(storedAppearance) {
        if (storedAppearance == pendingAppearance) pendingAppearance = null
    }
    var welcomeSeen by rememberSaveable {
        mutableStateOf(preferences.getBoolean("welcome_seen_v2", false))
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusComposerId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var matrixAccountsOpen by rememberSaveable { mutableStateOf(false) }
    var newMessageOpen by rememberSaveable { mutableStateOf(false) }
    var newMessageInitialRecipients by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val smsPermissions = remember {
        arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { smsConnectionViewModel.refresh() }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { contactsViewModel.refresh() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { picked ->
        selectedAttachments = (selectedAttachments + picked).distinct()
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
            permissionLauncher.launch(smsPermissions)
        } else {
            smsConnectionViewModel.refresh()
        }
    }
    val requestSmsAccess: () -> Unit = {
        val roleManager = context.getSystemService(RoleManager::class.java)
        when {
            roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_SMS) ->
                smsConnectionViewModel.reportError("This device does not offer the default SMS app role.")
            !roleManager.isRoleHeld(RoleManager.ROLE_SMS) ->
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            else -> permissionLauncher.launch(smsPermissions)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsConnectionViewModel.refresh()
                contactsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { smsConnectionViewModel.refresh() }
    LaunchedEffect(matrixAccountState.accounts, welcomeSeen) {
        if (welcomeSeen && Build.VERSION.SDK_INT >= 33 &&
            matrixAccountState.accounts.any { it.connectionState == "CONNECTED" } &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("matrix_notification_permission_asked_v1", false)
        ) {
            preferences.edit { putBoolean("matrix_notification_permission_asked_v1", true) }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(matrixCallbackUrl) {
        if (matrixCallbackUrl != null) {
            matrixAccountsOpen = true
            matrixAccountViewModel.finishBrowserLogin(matrixCallbackUrl)
            onMatrixCallbackHandled()
        }
    }
    LaunchedEffect(sharedAttachments, welcomeSeen) {
        if (sharedAttachments.isNotEmpty() && welcomeSeen) {
            selectedAttachments = (selectedAttachments + sharedAttachments).distinct()
            onShareHandled()
            newMessageOpen = true
        }
    }
    LaunchedEffect(requestedRecipient, welcomeSeen) {
        if (requestedRecipient != null && welcomeSeen) {
            newMessageInitialRecipients = listOf(requestedRecipient)
            newMessageOpen = true
            onRecipientHandled()
        }
    }
    LaunchedEffect(newMessageOpen, hasContactsPermission, smsState.ready) {
        if (newMessageOpen && smsState.ready && !hasContactsPermission &&
            !preferences.getBoolean("contacts_permission_requested_v1", false)
        ) {
            preferences.edit { putBoolean("contacts_permission_requested_v1", true) }
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    LaunchedEffect(selectedId) { conversationViewModel.selectThread(selectedId) }
    LaunchedEffect(selectedId) { selectedAttachments = emptyList() }
    val selected = selectedId?.let { id ->
        conversationState.conversation?.takeIf { it.id == id }
            ?: inboxState.conversations.firstOrNull { it.id == id }
    }
    val region = remember(context) { PhoneNumbers.deviceRegion(context) }
    val smsAccessBanner = when {
        !smsState.isDefaultApp -> "Set UnifiedMessages as your default SMS app to send and receive."
        !smsState.ready -> "Allow SMS access to send and receive messages."
        !smsState.mmsReady -> "Allow MMS access to receive picture messages."
        else -> null
    }
    val sendFromThread: (String, String, List<Uri>, (Boolean) -> Unit) -> Unit = { threadId, text, files, complete ->
        messageSendViewModel.send(threadId, text, files) { accepted, _ ->
            if (accepted && selectedId == threadId) {
                selectedAttachments = selectedAttachments.filterNot { it in files }
            }
            complete(accepted)
        }
    }

    BackHandler(enabled = newMessageOpen || matrixAccountsOpen || settingsOpen || selectedId != null) {
        when {
            newMessageOpen -> {
                newMessageOpen = false
                newMessageInitialRecipients = emptyList()
                selectedAttachments = emptyList()
            }
            matrixAccountsOpen -> matrixAccountsOpen = false
            settingsOpen -> settingsOpen = false
            else -> {
                selectedId = null
                focusComposerId = null
            }
        }
    }

    UnifiedMessagesTheme(settings = appearance) {
        val theme = LocalAppTheme.current
        val view = LocalView.current
        SideEffect {
            (context as? Activity)?.let { activity ->
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = welcomeSeen && !theme.isDark
                    isAppearanceLightNavigationBars = welcomeSeen && !theme.isDark
                }
            }
        }
        val hazeState = rememberHazeState(blurEnabled = Build.VERSION.SDK_INT >= 31)
        CompositionLocalProvider(LocalAppHazeState provides hazeState) {
            Box(Modifier.fillMaxSize().background(if (welcomeSeen) theme.background else theme.welcomeBackground)) {
                if (welcomeSeen) AppBackdrop(Modifier.matchParentSize())
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val expanded = maxWidth >= 840.dp
                    if (!welcomeSeen) {
                        WelcomeScreen(
                            onContinue = {
                                welcomeSeen = true
                                preferences.edit { putBoolean("welcome_seen_v2", true) }
                                requestSmsAccess()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (newMessageOpen) {
                        NewMessageScreen(
                            region = region,
                            contacts = contacts.map { contact ->
                                NewMessageContact(
                                    name = contact.displayName,
                                    e164Number = contact.normalizedNumber,
                                    photoUri = contact.photoUri,
                                )
                            },
                            hasSmsAccess = smsState.ready,
                            smsAccessReason = smsAccessBanner ?: "Allow SMS and MMS access to send and receive messages.",
                            onRequestSmsRole = requestSmsAccess,
                            onSend = { recipients, body, attachments, complete ->
                                if (!smsState.ready) {
                                    requestSmsAccess()
                                    complete(null, smsAccessBanner ?: "Allow SMS and MMS access to send and receive messages.")
                                } else {
                                    smsConnectionViewModel.startThread(recipients, region) { threadId, error ->
                                        if (threadId == null) {
                                            complete(null, error)
                                        } else {
                                            messageSendViewModel.send(
                                                threadId, body, attachments, showErrorDialog = false,
                                            ) { accepted, sendFailure ->
                                                complete(threadId.takeIf { accepted },
                                                    sendFailure)
                                                if (accepted) {
                                                    newMessageOpen = false
                                                    newMessageInitialRecipients = emptyList()
                                                    selectedAttachments = emptyList()
                                                    selectedId = threadId
                                                    focusComposerId = threadId
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onBack = {
                                newMessageOpen = false
                                newMessageInitialRecipients = emptyList()
                                selectedAttachments = emptyList()
                            },
                            initialRecipients = newMessageInitialRecipients,
                            attachments = selectedAttachments,
                            onAttach = { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                            onRemoveAttachment = { file ->
                                selectedAttachments = selectedAttachments - file
                            },
                            contactsEnabled = hasContactsPermission,
                            onRequestContacts = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (matrixAccountsOpen) {
                        MatrixAccountsScreen(
                            state = matrixAccountState,
                            onDiscover = matrixAccountViewModel::discover,
                            onPasswordLogin = matrixAccountViewModel::loginPassword,
                            onSsoLogin = { homeserver ->
                                matrixAccountViewModel.beginSso(homeserver) { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            onOidcLogin = { homeserver ->
                                matrixAccountViewModel.beginOidc(homeserver) { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            onLogout = matrixAccountViewModel::logout,
                            onRecoverKeys = matrixAccountViewModel::recoverKeys,
                            onSetBridgeBot = matrixAccountViewModel::setBridgeBot,
                            onBack = { matrixAccountsOpen = false },
                        )
                    } else if (settingsOpen) {
                        SettingsScreen(
                            settings = appearance,
                            onStyleChange = { style: SurfaceStyle ->
                                val current = pendingAppearance ?: storedAppearance
                                if (current.style != style) {
                                    pendingAppearance = current.copy(style = style)
                                    scope.launch {
                                        try {
                                            appearanceRepository.updateStyle(style)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            pendingAppearance = null
                                        }
                                    }
                                }
                            },
                            onBrightnessChange = { brightness: Brightness ->
                                val current = pendingAppearance ?: storedAppearance
                                if (current.brightness != brightness) {
                                    pendingAppearance = current.copy(brightness = brightness)
                                    scope.launch {
                                        try {
                                            appearanceRepository.updateBrightness(brightness)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            pendingAppearance = null
                                        }
                                    }
                                }
                            },
                            onAccentChange = { argb: Int ->
                                val current = pendingAppearance ?: storedAppearance
                                if (current.accentArgb != argb) {
                                    pendingAppearance = current.copy(accentArgb = argb)
                                    scope.launch {
                                        try {
                                            appearanceRepository.updateAccent(argb)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            pendingAppearance = null
                                        }
                                    }
                                }
                            },
                            showSenderDeletedMessages = showSenderDeletedMessages,
                            onShowSenderDeletedMessagesChange = { show ->
                                scope.launch { deletedMessagePreferences.setShowSenderDeletedMessages(show) }
                            },
                            onMatrixAccounts = { matrixAccountsOpen = true },
                            onBack = { settingsOpen = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (expanded) {
                        Row(Modifier.fillMaxSize()) {
                            InboxScreen(
                                conversations = inboxState.conversations,
                                query = inboxState.query,
                                isSearching = inboxState.isSearching,
                                onQueryChange = inboxViewModel::setQuery,
                                source = inboxState.source,
                                availableSources = inboxState.availableSources,
                                onSourceChange = inboxViewModel::setSource,
                                onConversationSelected = { selectedId = it },
                                onPinToggle = inboxViewModel::setPinned,
                                onNewMessage = {
                                    newMessageInitialRecipients = emptyList()
                                    newMessageOpen = true
                                },
                                smsAccessBanner = smsAccessBanner,
                                onRequestSmsAccess = requestSmsAccess,
                                onSettings = { settingsOpen = true },
                                selectedId = selectedId,
                                modifier = Modifier.width(390.dp),
                            )
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected == null) {
                                    EmptyConversationHint()
                                } else {
                                    ConversationScreen(
                                        conversation = selected,
                                        searchQuery = conversationState.searchQuery,
                                        onSearchQueryChange = conversationViewModel::setSearchQuery,
                                        sendAvailability = messageSendViewModel.availability(selected, region),
                                        onSend = { text, files, complete ->
                                            sendFromThread(selected.id, text, files, complete)
                                        },
                                        attachments = selectedAttachments,
                                        onAttach = { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                                        onRemoveAttachment = { file -> selectedAttachments = selectedAttachments - file },
                                        onRetry = messageSendViewModel::retry,
                                        onLoadMatrixActions = messageSendViewModel::loadMatrixActions,
                                        onReact = messageSendViewModel::react,
                                        onEdit = messageSendViewModel::edit,
                                        onUnsend = messageSendViewModel::unsend,
                                        onReplySend = { targetId, body, complete ->
                                            messageSendViewModel.reply(selected.id, targetId, body, complete)
                                        },
                                        onDraftTyping = messageSendViewModel::typing,
                                        smsAccessBanner = smsAccessBanner.takeIf { selected.source == MessageSource.SMS },
                                        onRequestSmsAccess = requestSmsAccess,
                                        onBack = { selectedId = null },
                                        showBack = false,
                                        autoFocusComposer = focusComposerId == selected.id,
                                        onComposerFocused = { focusComposerId = null },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    } else {
                        Crossfade(
                            targetState = selectedId,
                            label = "Conversation navigation",
                            modifier = Modifier.fillMaxSize(),
                        ) { conversationId ->
                            val conversation = conversationState.conversation?.takeIf { it.id == conversationId }
                                ?: inboxState.conversations.firstOrNull { it.id == conversationId }
                            if (conversationId == null) {
                                InboxScreen(
                                    conversations = inboxState.conversations,
                                    query = inboxState.query,
                                    isSearching = inboxState.isSearching,
                                    onQueryChange = inboxViewModel::setQuery,
                                    source = inboxState.source,
                                    availableSources = inboxState.availableSources,
                                    onSourceChange = inboxViewModel::setSource,
                                    onConversationSelected = { selectedId = it },
                                    onPinToggle = inboxViewModel::setPinned,
                                    onNewMessage = {
                                        newMessageInitialRecipients = emptyList()
                                        newMessageOpen = true
                                    },
                                    smsAccessBanner = smsAccessBanner,
                                    onRequestSmsAccess = requestSmsAccess,
                                    onSettings = { settingsOpen = true },
                                    selectedId = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (conversation != null) {
                                ConversationScreen(
                                    conversation = conversation,
                                    searchQuery = conversationState.searchQuery,
                                    onSearchQueryChange = conversationViewModel::setSearchQuery,
                                    sendAvailability = messageSendViewModel.availability(conversation, region),
                                    onSend = { text, files, complete ->
                                        sendFromThread(conversation.id, text, files, complete)
                                    },
                                    attachments = selectedAttachments,
                                    onAttach = { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                                    onRemoveAttachment = { file -> selectedAttachments = selectedAttachments - file },
                                    onRetry = messageSendViewModel::retry,
                                    onLoadMatrixActions = messageSendViewModel::loadMatrixActions,
                                    onReact = messageSendViewModel::react,
                                    onEdit = messageSendViewModel::edit,
                                    onUnsend = messageSendViewModel::unsend,
                                    onReplySend = { targetId, body, complete ->
                                        messageSendViewModel.reply(conversation.id, targetId, body, complete)
                                    },
                                    onDraftTyping = messageSendViewModel::typing,
                                    smsAccessBanner = smsAccessBanner.takeIf { conversation.source == MessageSource.SMS },
                                    onRequestSmsAccess = requestSmsAccess,
                                    onBack = { selectedId = null },
                                    showBack = true,
                                    autoFocusComposer = focusComposerId == conversation.id,
                                    onComposerFocused = { focusComposerId = null },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                EmptyConversationHint()
                            }
                        }
                    }
                }
            }
            smsState.error?.let { error ->
                AppMessageDialog(
                    onDismissRequest = smsConnectionViewModel::clearError,
                    title = "Messaging issue",
                    message = error,
                )
            }
            sendError?.let { error ->
                AppMessageDialog(
                    onDismissRequest = messageSendViewModel::clearError,
                    title = "Messaging issue",
                    message = error,
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationHint() {
    val theme = LocalAppTheme.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            DotMatrixAccent(columns = 8, rows = 4, dotSize = 3.dp, dotSpacing = 9.dp, color = theme.accent)
            Text(
                text = "Select a chat",
                style = MaterialTheme.typography.titleLarge,
                color = theme.onSurface,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = "Choose a conversation to see its messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
