package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.data.InboxSearch
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.ui.components.ContactAvatar
import com.unifiedmessages.app.ui.components.SourceBadge
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette
import com.unifiedmessages.app.ui.theme.VisualStyle

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
    var settingsMenuOpen by rememberSaveable { mutableStateOf(false) }
    var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showNewMessageInfo by rememberSaveable { mutableStateOf(false) }
    val source = MessageSource.entries.firstOrNull { it.name == sourceName }
    val results = InboxSearch.filter(SampleConversationRepository.conversations, query, source)
    val pinned = results.filter { it.isPinned }
    val other = results.filterNot { it.isPinned }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 22.dp, bottom = 15.dp),
            ) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.primaryText,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { settingsMenuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = palette.primaryText)
                    }
                    DropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                settingsMenuOpen = false
                                onSettings()
                            },
                        )
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 21.dp, end = 16.dp, top = 7.dp),
            ) {
                Box(Modifier.size(6.dp).background(palette.accent, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Sample chats only",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryText,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { sourceMenuOpen = true }) {
                        Text(
                            text = (source?.label ?: "All sources") + "  ⌄",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.primaryText,
                        )
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        (listOf(null) + MessageSource.entries).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option?.label ?: "All sources") },
                                onClick = {
                                    sourceName = option?.name ?: "ALL"
                                    sourceMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No sample chats match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryText,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 94.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item {
                            SectionLabel("Pinned")
                            ConversationGroup(pinned, selectedId, onConversationSelected)
                        }
                    }
                    if (other.isNotEmpty()) {
                        item {
                            SectionLabel("All")
                            ConversationGroup(other, selectedId, onConversationSelected)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewMessageInfo = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 22.dp).size(58.dp),
            shape = CircleShape,
            containerColor = palette.accent,
            contentColor = Color.White,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "New message information", modifier = Modifier.size(27.dp))
        }
    }

    if (showNewMessageInfo) {
        AlertDialog(
            onDismissRequest = { showNewMessageInfo = false },
            title = { Text("Preview only") },
            text = { Text("New messages are unavailable. These are fictional sample chats; messaging is not connected yet.") },
            confirmButton = {
                TextButton(onClick = { showNewMessageInfo = false }) { Text("Got it") }
            },
            containerColor = palette.surface,
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    val palette = LocalUnifiedPalette.current
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = palette.secondaryText,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ConversationGroup(
    conversations: List<Conversation>,
    selectedId: String?,
    onConversationSelected: (String) -> Unit,
) {
    val palette = LocalUnifiedPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface),
    ) {
        conversations.forEachIndexed { index, conversation ->
            ConversationRow(
                conversation = conversation,
                selected = conversation.id == selectedId,
                onClick = { onConversationSelected(conversation.id) },
            )
            if (index < conversations.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp, end = 14.dp),
                    thickness = 1.dp,
                    color = palette.outline.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalUnifiedPalette.current
    val selectedColor = if (palette.style == VisualStyle.CHATS) Color(0xFFF1F4FF) else palette.surfaceElevated
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) selectedColor else palette.surface)
            .clickable(role = Role.Button, onClickLabel = "Open ${conversation.title} conversation", onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 12.dp),
    ) {
        ContactAvatar(text = conversation.avatarText, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = conversation.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(conversation.source.label)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier.size(20.dp).background(palette.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
