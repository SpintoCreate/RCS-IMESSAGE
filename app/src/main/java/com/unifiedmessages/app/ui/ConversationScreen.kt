package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.model.ChatMessage
import com.unifiedmessages.app.model.Conversation
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

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = if (showBack) 5.dp else 22.dp, end = 20.dp, top = 13.dp, bottom = 14.dp),
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to inbox", tint = palette.primaryText)
                }
                Spacer(Modifier.width(5.dp))
            }
            Box(
                Modifier.size(44.dp).background(palette.surfaceHighlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(conversation.avatarText, color = palette.primaryText, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.title, color = palette.primaryText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                SourceBadge(conversation.source.label)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.outline.copy(alpha = 0.55f)))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item {
                Text(
                    text = "SAMPLE CONVERSATION · ${conversation.source.label.uppercase()}",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
                )
            }
            items(conversation.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(palette.surface.copy(alpha = 0.95f))
                .padding(horizontal = 17.dp, vertical = 12.dp),
        ) {
            Text(
                text = "PREVIEW MODE  ·  SENDING IS UNAVAILABLE",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Compose a draft") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.surfaceElevated,
                        unfocusedContainerColor = palette.surfaceElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(7.dp))
                IconButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Sending unavailable in preview" },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = palette.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val palette = LocalUnifiedPalette.current
    val shape = RoundedCornerShape(
        topStart = 21.dp,
        topEnd = 21.dp,
        bottomEnd = if (message.isMine) 6.dp else 21.dp,
        bottomStart = if (message.isMine) 21.dp else 6.dp,
    )
    val base = if (message.isMine) palette.outgoingBubble else palette.incomingBubble
    val textColor = if (message.isMine) palette.onOutgoingBubble else palette.onIncomingBubble
    val brush = if (palette.style == VisualStyle.LIQUID_GLASS) {
        Brush.linearGradient(listOf(base.copy(alpha = 0.91f), palette.surfaceHighlight.copy(alpha = 0.93f)))
    } else {
        Brush.linearGradient(listOf(base, base))
    }

    Row(
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(brush)
                    .border(1.dp, if (palette.style == VisualStyle.LIQUID_GLASS) palette.outline.copy(alpha = 0.7f) else Color.Transparent, shape)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Text(
                text = message.time,
                color = palette.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 5.dp, start = 3.dp, end = 3.dp),
            )
        }
    }
}
