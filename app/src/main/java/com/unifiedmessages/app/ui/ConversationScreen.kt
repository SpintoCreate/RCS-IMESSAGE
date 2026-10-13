package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.ui.components.ContactAvatar
import com.unifiedmessages.app.ui.components.SourceBadge
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette
import com.unifiedmessages.app.ui.theme.VisualStyle

@Composable
fun ConversationScreen(
    conversation: Conversation,
    onBack: () -> Unit,
    showBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUnifiedPalette.current
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var searchOpen by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(conversation.id) { mutableStateOf("") }
    val visibleMessages = if (searchQuery.isBlank()) conversation.messages else {
        conversation.messages.filter { it.text.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.background(palette.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (showBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to chats",
                        tint = palette.primaryText,
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(text = conversation.avatarText, size = 42.dp)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                SourceBadge(source = conversation.source.label)
            }

            IconButton(
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) searchQuery = ""
                },
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
            ) {
                Icon(
                    if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (searchOpen) "Close conversation search" else "Search sample messages",
                    tint = palette.primaryText,
                )
            }
        }

        if (searchOpen) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                placeholder = { Text("Search sample messages") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.incomingBubble,
                    unfocusedContainerColor = palette.incomingBubble,
                    focusedTextColor = palette.onIncomingBubble,
                    unfocusedTextColor = palette.onIncomingBubble,
                    focusedPlaceholderColor = palette.secondaryText,
                    unfocusedPlaceholderColor = palette.secondaryText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = palette.accent,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item(key = "sample-day-label") {
                Text(
                    text = "SAMPLE CONVERSATION",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
            if (visibleMessages.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        text = "No sample messages match your search.",
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    )
                }
            }
            items(visibleMessages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        ) {
            Text(
                text = "Preview only · messages cannot be sent",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a draft") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.incomingBubble,
                        unfocusedContainerColor = palette.incomingBubble,
                        focusedTextColor = palette.onIncomingBubble,
                        unfocusedTextColor = palette.onIncomingBubble,
                        focusedPlaceholderColor = palette.secondaryText,
                        unfocusedPlaceholderColor = palette.secondaryText,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = palette.accent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(palette.accent.copy(alpha = 0.78f), CircleShape)
                        .semantics {
                            contentDescription = "Send unavailable in preview mode"
                            disabled()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val palette = LocalUnifiedPalette.current
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = if (message.isMine) 5.dp else 16.dp,
        bottomStart = if (message.isMine) 16.dp else 5.dp,
    )
    val base = if (message.isMine) palette.outgoingBubble else palette.incomingBubble
    val textColor = if (message.isMine) palette.onOutgoingBubble else palette.onIncomingBubble
    val isGlass = palette.style == VisualStyle.LIQUID_GLASS
    val brush = if (isGlass) {
        Brush.linearGradient(listOf(base.copy(alpha = 0.93f), palette.surfaceHighlight.copy(alpha = 0.93f)))
    } else {
        Brush.linearGradient(listOf(base, base))
    }

    Row(
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(brush)
                    .border(1.dp, if (isGlass) palette.outline.copy(alpha = 0.6f) else Color.Transparent, shape)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
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
                color = palette.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, start = 3.dp, end = 3.dp),
            )
        }
    }
}
