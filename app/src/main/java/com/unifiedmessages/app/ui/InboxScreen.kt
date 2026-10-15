package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.model.Conversation
import com.unifiedmessages.app.model.MessageSource
import com.unifiedmessages.app.ui.components.AppCard
import com.unifiedmessages.app.ui.components.AppDropdownMenu
import com.unifiedmessages.app.ui.components.AppFab
import com.unifiedmessages.app.ui.components.AppSurface
import com.unifiedmessages.app.ui.components.AppTopBar
import com.unifiedmessages.app.ui.components.ContactAvatar
import com.unifiedmessages.app.ui.components.SmsAccessBanner
import com.unifiedmessages.app.ui.components.SourceBadge
import com.unifiedmessages.app.ui.components.overlayScrimColor
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle

@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    query: String,
    onQueryChange: (String) -> Unit,
    source: MessageSource?,
    availableSources: List<MessageSource> = listOf(MessageSource.SMS),
    onSourceChange: (MessageSource?) -> Unit,
    onConversationSelected: (String) -> Unit,
    onPinToggle: (String, Boolean) -> Unit,
    onNewMessage: () -> Unit,
    smsAccessBanner: String? = null,
    onRequestSmsAccess: () -> Unit = {},
    onSettings: () -> Unit,
    selectedId: String?,
    isSearching: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var searchField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    // Restore a saved field after process recreation without resetting its cursor during typing.
    LaunchedEffect(Unit) {
        if (searchField.text != query) onQueryChange(searchField.text)
    }
    val searchText = searchField.text.trim()
    var settingsMenuOpen by rememberSaveable { mutableStateOf(false) }
    var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
    val pinned = conversations.filter { it.isPinned }
    val other = conversations.filterNot { it.isPinned }
    val listState = rememberLazyListState()
    val scrolledFade by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
        }
    }
    var controlsHeightPx by remember { mutableIntStateOf(0) }
    val controlsHeight = with(LocalDensity.current) { controlsHeightPx.toDp() }

    Box(modifier = modifier) {
        if (isSearching || conversations.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(top = controlsHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        isSearching -> if (searchText.isNotEmpty()) "Searching…" else "Loading chats…"
                        searchText.isNotEmpty() -> "No results for “$searchText”"
                        source != null -> "No chats for this source."
                        else -> "No messages yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = controlsHeight, bottom = 94.dp),
            ) {
                if (searchText.isNotEmpty()) {
                    item {
                        ConversationGroup(conversations, selectedId, onConversationSelected, onPinToggle, searchText)
                    }
                } else {
                    if (pinned.isNotEmpty()) {
                        item {
                            SectionLabel("Pinned")
                            ConversationGroup(pinned, selectedId, onConversationSelected, onPinToggle)
                        }
                    }
                    if (other.isNotEmpty()) {
                        item {
                            SectionLabel("All")
                            ConversationGroup(other, selectedId, onConversationSelected, onPinToggle)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { controlsHeightPx = it.height }
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        ) {
            AppTopBar(
                modifier = Modifier.fillMaxWidth(),
                color = if (theme.style == SurfaceStyle.SOLID) theme.background else null,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 22.dp, bottom = 15.dp),
                ) {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.headlineLarge,
                        color = theme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { settingsMenuOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = theme.onSurface)
                        }
                        AppDropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
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
            }

            if (smsAccessBanner != null) {
                SmsAccessBanner(smsAccessBanner, onRequestSmsAccess)
            }

            Column(
                Modifier.background(
                    Brush.verticalGradient(
                        0f to theme.background.copy(alpha = 0.96f * scrolledFade),
                        0.58f to theme.background.copy(alpha = 0.94f * scrolledFade),
                        0.84f to theme.background.copy(alpha = 0.72f * scrolledFade),
                        1f to Color.Transparent,
                    ),
                ),
            ) {
                AppSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = CircleShape,
                    color = theme.surface,
                ) {
                    TextField(
                        value = searchField,
                        onValueChange = { updated ->
                            searchField = updated
                            onQueryChange(updated.text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search chats") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            focus.clearFocus()
                        }),
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchField.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchField = TextFieldValue("")
                                    onQueryChange("")
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = theme.onSurface,
                            unfocusedTextColor = theme.onSurface,
                            focusedPlaceholderColor = theme.onSurfaceVariant,
                            unfocusedPlaceholderColor = theme.onSurfaceVariant,
                            focusedLeadingIconColor = theme.onSurfaceVariant,
                            unfocusedLeadingIconColor = theme.onSurfaceVariant,
                            focusedTrailingIconColor = theme.onSurfaceVariant,
                            unfocusedTrailingIconColor = theme.onSurfaceVariant,
                            cursorColor = theme.accent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 21.dp, end = 16.dp, top = 7.dp),
                ) {
                    Box(Modifier.size(6.dp).background(theme.accent, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { sourceMenuOpen = true }) {
                            Text(
                                text = (source?.label ?: "All sources") + "  ⌄",
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.onSurface,
                            )
                        }
                        AppDropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                            val sourceOptions = listOf<MessageSource?>(null) +
                                (availableSources + listOfNotNull(source)).distinct()
                            sourceOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option?.label ?: "All sources") },
                                    onClick = {
                                        onSourceChange(option)
                                        sourceMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        AppFab(
            onClick = onNewMessage,
            contentDescription = "New message",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 22.dp).size(58.dp),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = theme.onAccent,
                modifier = Modifier.align(Alignment.Center).size(27.dp),
            )
        }
        if (settingsMenuOpen || sourceMenuOpen) {
            Box(
                Modifier.matchParentSize()
                    .background(overlayScrimColor())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close menu",
                    ) {
                        settingsMenuOpen = false
                        sourceMenuOpen = false
                    },
            )
        }
    }

}

@Composable
private fun SectionLabel(label: String) {
    val theme = LocalAppTheme.current
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = theme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ConversationGroup(
    conversations: List<Conversation>,
    selectedId: String?,
    onConversationSelected: (String) -> Unit,
    onPinToggle: (String, Boolean) -> Unit,
    searchQuery: String = "",
) {
    val theme = LocalAppTheme.current
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = theme.surface,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            conversations.forEachIndexed { index, conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = conversation.id == selectedId,
                    onClick = { onConversationSelected(conversation.id) },
                    onPinToggle = { onPinToggle(conversation.id, !conversation.isPinned) },
                    searchQuery = searchQuery,
                )
                if (index < conversations.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 14.dp),
                        thickness = 1.dp,
                        color = theme.outline.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    searchQuery: String,
) {
    val theme = LocalAppTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) theme.selectedSurface else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open ${conversation.title} conversation",
                onLongClickLabel = if (conversation.isPinned) "Unpin ${conversation.title}" else "Pin ${conversation.title}",
                onClick = onClick,
                onLongClick = onPinToggle,
            )
            .padding(horizontal = 11.dp, vertical = 12.dp),
    ) {
        ContactAvatar(text = conversation.avatarText, size = 44.dp, photoUri = conversation.photoUri)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = highlightedMatch(conversation.title, searchQuery, theme.accent, theme.onSurface),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    color = theme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = conversation.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.source != MessageSource.OTHER) {
                    SourceBadge(conversation.source.label)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = highlightedMatch(conversation.preview, searchQuery, theme.accent, theme.onSurface),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier.size(20.dp).background(theme.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.onAccent,
                        )
                    }
                }
            }
        }
    }
}

/** Emphasize the matching portion of a Room snippet without changing row dimensions. */
private fun highlightedMatch(text: String, query: String, accent: Color, onSurface: Color): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(
        color = onSurface,
        background = accent.copy(alpha = 0.18f),
        fontWeight = FontWeight.Bold,
    )
    return buildAnnotatedString {
        append(text)
        var start = text.indexOf(trimmed, ignoreCase = true)
        while (start >= 0) {
            addStyle(style, start, start + trimmed.length)
            start = text.indexOf(trimmed, startIndex = start + trimmed.length, ignoreCase = true)
        }
        // A digit-only query can match formatted numbers with separators in between.
        if (text.indexOf(trimmed, ignoreCase = true) < 0 && trimmed.any(Char::isDigit) &&
            trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        ) {
            val needle = trimmed.filter(Char::isDigit)
            val digitPositions = text.indices.filter { text[it].isDigit() }
            val digits = digitPositions.joinToString("") { text[it].toString() }
            val digitStart = digits.indexOf(needle)
            if (needle.isNotEmpty() && digitStart >= 0) {
                addStyle(style, digitPositions[digitStart], digitPositions[digitStart + needle.length - 1] + 1)
            }
        }
    }
}
