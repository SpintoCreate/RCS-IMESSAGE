package com.unifiedmessages.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.data.local.DeliveryStatus
import com.unifiedmessages.app.ui.components.AppBubble
import com.unifiedmessages.app.ui.components.AppFab
import com.unifiedmessages.app.ui.components.AppInputBar
import com.unifiedmessages.app.ui.components.AppSurface
import com.unifiedmessages.app.ui.components.AppTopBar
import com.unifiedmessages.app.ui.components.ContactAvatar
import com.unifiedmessages.app.ui.components.SmsAccessBanner
import com.unifiedmessages.app.ui.components.SourceBadge
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle

@Composable
fun ConversationScreen(
    conversation: Conversation,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    canSend: Boolean,
    onSend: (String, List<Uri>, (Boolean) -> Unit) -> Unit,
    attachments: List<Uri> = emptyList(),
    onAttach: () -> Unit = {},
    onRemoveAttachment: (Uri) -> Unit = {},
    onRetry: (String) -> Unit = {},
    smsAccessBanner: String? = null,
    onRequestSmsAccess: () -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean,
    autoFocusComposer: Boolean = false,
    onComposerFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalAppTheme.current
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var sendPending by remember(conversation.id) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(conversation.id) { mutableStateOf(false) }
    val composerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocusComposer, conversation.id) {
        if (autoFocusComposer && canSend) {
            composerFocusRequester.requestFocus()
            onComposerFocused()
        }
    }
    val visibleMessages = conversation.messages

    val screenModifier = if (palette.style == SurfaceStyle.SOLID) {
        modifier.background(palette.background)
    } else {
        modifier
    }
    var topInsetPx by remember { mutableIntStateOf(0) }
    var bottomInsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topInset = with(density) { topInsetPx.toDp() }
    val bottomInset = with(density) { bottomInsetPx.toDp() }

    Box(modifier = screenModifier) {
        // The measured insets keep the resting layout unchanged while bubbles scroll behind the header.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = topInset + 20.dp,
                bottom = bottomInset + 20.dp,
            ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item(key = "conversation-day-label") {
                Text(
                    text = "CONVERSATION",
                    color = palette.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
            if (visibleMessages.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No messages match your search." else "No messages yet.",
                        color = palette.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    )
                }
            }
            items(visibleMessages, key = { it.id }) { message ->
                MessageBubble(message, onRetry)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topInsetPx = it.height }
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        ) {
            AppTopBar(
                modifier = Modifier
                    .fillMaxWidth(),
                color = palette.background,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).size(48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to chats",
                            tint = palette.onSurface,
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContactAvatar(text = conversation.avatarText, size = 42.dp, photoUri = conversation.photoUri)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (conversation.source == MessageSource.SMS) {
                        SourceBadge(source = conversation.source.label)
                    }
                }

                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) onSearchQueryChange("")
                    },
                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                ) {
                    Icon(
                        if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (searchOpen) "Close conversation search" else "Search messages",
                        tint = palette.onSurface,
                    )
                }
            }

            if (smsAccessBanner != null) {
                SmsAccessBanner(smsAccessBanner, onRequestSmsAccess)
            }

            if (searchOpen) {
                AppSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.bubbleIncoming,
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search messages") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
            }
        }

        AppInputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomInsetPx = it.height },
            color = palette.background,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        ) {
            Column {
                Text(
                    text = if (canSend) "SMS" else "This sender can't receive replies",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSurface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        color = palette.bubbleIncoming,
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(composerFocusRequester),
                            enabled = canSend,
                            placeholder = { Text(if (canSend) "Message" else "This sender can't receive replies") },
                            trailingIcon = {
                                IconButton(onClick = onAttach, enabled = canSend && !sendPending) {
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
                                disabledTextColor = palette.onSurfaceVariant,
                                focusedPlaceholderColor = palette.onSurfaceVariant,
                                unfocusedPlaceholderColor = palette.onSurfaceVariant,
                                disabledPlaceholderColor = palette.onSurfaceVariant,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = palette.accent,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AppFab(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        onClick = if (canSend && (draft.isNotBlank() || attachments.isNotEmpty()) && !sendPending) ({
                            val submitted = draft.trim()
                            val submittedAttachments = attachments.toList()
                            sendPending = true
                            onSend(submitted, submittedAttachments) { accepted ->
                                sendPending = false
                                if (accepted && draft.trim() == submitted) draft = ""
                            }
                        }) else null,
                        contentDescription = if (canSend) "Send message" else "Send unavailable",
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

@Composable
private fun MessageBubble(message: ChatMessage, onRetry: (String) -> Unit) {
    val palette = LocalAppTheme.current
    var attachmentsOpen by remember(message.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = if (message.isMine) 5.dp else 16.dp,
        bottomStart = if (message.isMine) 16.dp else 5.dp,
    )
    val textColor = if (message.isMine) palette.onBubbleOutgoing else palette.onBubbleIncoming

    Row(
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            AppBubble(
                outgoing = message.isMine,
                modifier = if (message.attachments.isNotEmpty()) {
                    Modifier.clickable(onClickLabel = "Open media attachments") { attachmentsOpen = true }
                } else Modifier,
                shape = shape,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Text(
                text = message.time,
                color = palette.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, start = 3.dp, end = 3.dp),
            )
            if (message.isMine && message.deliveryStatus == DeliveryStatus.FAILED) {
                TextButton(onClick = { onRetry(message.id) }) {
                    Text("Retry", color = palette.accent)
                }
            }
        }
    }
    if (attachmentsOpen) {
        MessageAttachmentsDialog(message.attachments, onDismiss = { attachmentsOpen = false })
    }
}
