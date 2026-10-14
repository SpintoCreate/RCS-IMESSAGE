package com.unifiedmessages.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.sms.PhoneNumbers
import com.unifiedmessages.app.ui.components.AppCard
import com.unifiedmessages.app.ui.components.AppFab
import com.unifiedmessages.app.ui.components.AppInputBar
import com.unifiedmessages.app.ui.components.AppSurface
import com.unifiedmessages.app.ui.components.AppTopBar
import com.unifiedmessages.app.ui.components.ContactAvatar
import com.unifiedmessages.app.ui.components.SmsAccessBanner
import com.unifiedmessages.app.ui.theme.LocalAppTheme

/** Contacts are supplied by the caller's permission-aware Room cache. */
data class NewMessageContact(
    val name: String,
    val e164Number: String,
    val photoUri: String? = null,
)

/** Compose a message before a thread exists. The caller finds/creates the exact recipient thread. */
@Composable
fun NewMessageScreen(
    region: String,
    contacts: List<NewMessageContact>,
    hasSmsRole: Boolean,
    onRequestSmsRole: () -> Unit,
    onSend: (List<String>, String, List<Uri>, (threadId: String?, error: String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialRecipients: List<String> = emptyList(),
    attachments: List<Uri> = emptyList(),
    onAttach: () -> Unit = {},
    onRemoveAttachment: (Uri) -> Unit = {},
    contactsEnabled: Boolean = true,
    onRequestContacts: () -> Unit = {},
) {
    val palette = LocalAppTheme.current
    val keyboard = LocalSoftwareKeyboardController.current
    val toFocusRequester = remember { FocusRequester() }
    val composerFocusRequester = remember { FocusRequester() }
    var recipients by rememberSaveable(region, initialRecipients) {
        mutableStateOf(ArrayList(initialRecipients.mapNotNull { PhoneNumbers.normalizeToE164(it, region) }.distinct()))
    }
    var toInput by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var attemptedInvalid by rememberSaveable { mutableStateOf(false) }
    var sendPending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recipients.isEmpty()) {
        if (recipients.isEmpty()) toFocusRequester.requestFocus()
        else composerFocusRequester.requestFocus()
    }

    val trimmedInput = toInput.trim()
    val typedNumber = remember(trimmedInput, region) {
        PhoneNumbers.normalizeToE164(trimmedInput, region)
    }
    val typedDigits = remember(trimmedInput) { trimmedInput.filter(Char::isDigit) }
    val matchingContacts = remember(contacts, trimmedInput, typedDigits, recipients, region) {
        if (trimmedInput.isBlank()) emptyList() else contacts.asSequence()
            .filter { contact ->
                val e164 = PhoneNumbers.normalizeToE164(contact.e164Number, region)
                e164 != null && e164 !in recipients && (
                    contact.name.contains(trimmedInput, ignoreCase = true) ||
                        typedDigits.length >= 3 && e164.filter(Char::isDigit).contains(typedDigits)
                    )
            }
            .distinctBy { PhoneNumbers.normalizeToE164(it.e164Number, region) }
            .take(30)
            .toList()
    }

    fun addRecipient(number: String) {
        val normalized = PhoneNumbers.normalizeToE164(number, region)
        if (normalized == null) {
            attemptedInvalid = true
            return
        }
        if (normalized !in recipients) recipients = ArrayList(recipients + normalized)
        toInput = ""
        attemptedInvalid = false
        sendError = null
    }

    fun send() {
        if (sendPending || recipients.isEmpty() || (draft.isBlank() && attachments.isEmpty())) return
        if (!hasSmsRole) {
            onRequestSmsRole()
            return
        }
        keyboard?.hide()
        sendPending = true
        sendError = null
        onSend(recipients.toList(), draft.trim(), attachments.toList()) { threadId, error ->
            sendPending = false
            if (threadId == null) sendError = error ?: "Message could not be sent. Try again."
        }
    }

    Column(modifier.fillMaxSize()) {
        AppTopBar(modifier = Modifier.fillMaxWidth(), color = palette.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to chats", tint = palette.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                Text("New message", style = MaterialTheme.typography.headlineSmall, color = palette.onSurface)
            }
        }

        if (!hasSmsRole) {
            SmsAccessBanner(
                "Set UnifiedMessages as your default SMS app and allow SMS access to send.",
                onRequestSmsRole,
            )
        }

        AppSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = palette.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("To:", style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
                if (recipients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        recipients.forEach { recipient ->
                            AppSurface(
                                modifier = Modifier.padding(end = 6.dp),
                                shape = CircleShape,
                                color = palette.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        PhoneNumbers.formatForDisplay(recipient, region) ?: recipient,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onSurface,
                                    )
                                    IconButton(
                                        onClick = {
                                            recipients = ArrayList(recipients - recipient)
                                            sendError = null
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Remove $recipient",
                                            tint = palette.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextField(
                    value = toInput,
                    onValueChange = { value ->
                        // Many software keyboards emit an empty edit on Backspace in an empty To field.
                        if (value.isEmpty() && toInput.isEmpty() && recipients.isNotEmpty()) {
                            recipients = ArrayList(recipients.dropLast(1))
                        } else {
                            toInput = value
                        }
                        attemptedInvalid = false
                        sendError = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(toFocusRequester).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            addRecipient(toInput)
                            keyboard?.hide()
                            true
                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                            toInput.isEmpty() && recipients.isNotEmpty()
                        ) {
                            recipients = ArrayList(recipients.dropLast(1))
                            true
                        } else false
                    },
                    placeholder = { Text("Name or phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        addRecipient(toInput)
                        keyboard?.hide()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = palette.onSurface,
                        unfocusedTextColor = palette.onSurface,
                        focusedPlaceholderColor = palette.onSurfaceVariant,
                        unfocusedPlaceholderColor = palette.onSurfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = palette.accent,
                    ),
                )
                if ((typedNumber == null && (attemptedInvalid || typedDigits.length >= 7)) ||
                    (typedNumber != null && typedNumber in recipients)
                ) {
                    Text(
                        if (typedNumber != null && typedNumber in recipients) "This recipient is already added."
                        else "Enter a complete phone number to add a recipient.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (typedNumber != null && typedNumber !in recipients) {
                item(key = "typed-number") {
                    RecipientSuggestion(
                        title = "Send to ${PhoneNumbers.formatForDisplay(typedNumber, region) ?: typedNumber}",
                        subtitle = typedNumber,
                        onClick = { addRecipient(typedNumber) },
                    )
                }
            }
            if (matchingContacts.isNotEmpty()) {
                items(matchingContacts, key = { "contact-${it.e164Number}-${it.name}" }) { contact ->
                    RecipientSuggestion(
                        title = contact.name,
                        subtitle = PhoneNumbers.formatForDisplay(contact.e164Number, region) ?: contact.e164Number,
                        onClick = { addRecipient(contact.e164Number) },
                        avatar = contact.name,
                        photoUri = contact.photoUri,
                    )
                }
            } else if (trimmedInput.isNotBlank() && typedNumber == null && contactsEnabled) {
                item(key = "no-matches") {
                    Text(
                        "No matching contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (!contactsEnabled) {
                item(key = "contacts-permission") {
                    TextButton(onClick = onRequestContacts) {
                        Text("Allow contacts to search names", color = palette.accent)
                    }
                }
            }
        }

        if (recipients.isNotEmpty()) {
            AppInputBar(
                modifier = Modifier.fillMaxWidth(),
                color = palette.background,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            ) {
                Column {
                    Text(
                        if (recipients.size > 1 || attachments.isNotEmpty()) "MMS" else "SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                    )
                    if (attachments.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            attachments.forEachIndexed { index, attachment ->
                                TextButton(onClick = { onRemoveAttachment(attachment) }) {
                                    Text("Media ${index + 1}", color = palette.onSurface)
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove media attachment ${index + 1}",
                                        tint = palette.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (sendError != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sendError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = ::send, enabled = !sendPending) {
                                Text("Retry", color = palette.accent)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppSurface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            color = palette.bubbleIncoming,
                        ) {
                            TextField(
                                value = draft,
                                onValueChange = { draft = it; sendError = null },
                                modifier = Modifier.fillMaxWidth().focusRequester(composerFocusRequester),
                                placeholder = { Text("Message") },
                                trailingIcon = {
                                    IconButton(onClick = onAttach, enabled = !sendPending) {
                                        Icon(
                                            Icons.Outlined.AttachFile,
                                            contentDescription = "Attach media",
                                            tint = palette.onSurfaceVariant,
                                        )
                                    }
                                },
                                minLines = 1,
                                maxLines = 4,
                                shape = RoundedCornerShape(28.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = palette.onBubbleIncoming,
                                    unfocusedTextColor = palette.onBubbleIncoming,
                                    focusedPlaceholderColor = palette.onSurfaceVariant,
                                    unfocusedPlaceholderColor = palette.onSurfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = palette.accent,
                                ),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        AppFab(
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            onClick = if (!sendPending && (draft.isNotBlank() || attachments.isNotEmpty())) ::send else null,
                            contentDescription = if (hasSmsRole) "Send message" else "Enable SMS to send",
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = palette.onAccent,
                                modifier = Modifier.align(Alignment.Center).size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientSuggestion(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    avatar: String? = null,
    photoUri: String? = null,
) {
    val palette = LocalAppTheme.current
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = title, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null) {
                ContactAvatar(text = avatar, size = 38.dp, photoUri = photoUri)
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
