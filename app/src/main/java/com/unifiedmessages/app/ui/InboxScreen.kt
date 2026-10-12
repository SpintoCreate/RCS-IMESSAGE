package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.data.InboxSearch
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.components.SourceBadge
import com.unifiedmessages.app.ui.components.StyleSurface
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette

@Composable
fun InboxScreen(
    onConversationSelected: (String) -> Unit,
    onSettings: () -> Unit,
    selectedId: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUnifiedPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var sourceName by rememberSaveable { mutableStateOf("ALL") }
    val source = MessageSource.entries.firstOrNull { it.name == sourceName }
    val results = InboxSearch.filter(SampleConversationRepository.conversations, query, source)

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotMatrixAccent(columns = 3, rows = 2, dotSize = 2.dp, dotSpacing = 4.dp, color = palette.accent)
                    Text(
                        text = "  UNIFIED / PREVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                    )
                }
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.primaryText,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Open settings", tint = palette.primaryText)
            }
        }

        Text(
            text = "ONE INBOX. EVERY CONVERSATION.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryText,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 18.dp),
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Search sample chats") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            listOf("ALL", "IMESSAGE", "RCS", "SMS").forEach { option ->
                val label = if (option == "ALL") "All" else MessageSource.valueOf(option).label
                FilterChip(
                    selected = sourceName == option,
                    onClick = { sourceName = option },
                    label = { Text(label) },
                )
            }
        }

        StyleSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(palette.accent, CircleShape))
                Text(
                    text = "  SAMPLE DATA  ·  NOTHING IS CONNECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                )
            }
        }

        Text(
            text = "CONVERSATIONS  /  ${results.size.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondaryText,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 10.dp),
        )

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sample chats match your search.", color = palette.secondaryText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = conversation.id == selectedId,
                        onClick = { onConversationSelected(conversation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalUnifiedPalette.current
    StyleSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics {
            contentDescription = "${conversation.title}, ${conversation.source.label}, ${conversation.unreadCount} unread. ${conversation.preview}"
        },
        shape = RoundedCornerShape(if (selected) 20.dp else 22.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(49.dp).background(palette.surfaceHighlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(conversation.avatarText, color = palette.primaryText, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                        color = palette.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(conversation.timestamp, style = MaterialTheme.typography.labelSmall, color = palette.secondaryText)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(conversation.source.label)
                    Spacer(Modifier.weight(1f))
                    if (conversation.unreadCount > 0) {
                        Box(
                            modifier = Modifier.size(23.dp).background(palette.accent, CircleShape)
                                .semantics { contentDescription = "${conversation.unreadCount} unread messages" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.background,
                            )
                        }
                    }
                }
            }
        }
    }
}
