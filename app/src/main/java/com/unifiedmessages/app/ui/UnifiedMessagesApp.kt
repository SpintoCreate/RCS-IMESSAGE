package com.unifiedmessages.app.ui

import android.content.Context
import androidx.core.content.edit
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette
import com.unifiedmessages.app.ui.theme.UnifiedMessagesTheme
import com.unifiedmessages.app.ui.theme.VisualStyle

@Composable
fun UnifiedMessagesApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("unified_messages_display", Context.MODE_PRIVATE)
    }
    var styleName by rememberSaveable {
        mutableStateOf(preferences.getString("visual_style", VisualStyle.NOTHING.name) ?: VisualStyle.NOTHING.name)
    }
    val style = VisualStyle.entries.firstOrNull { it.name == styleName } ?: VisualStyle.NOTHING
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val selected = selectedId?.let(SampleConversationRepository::findById)

    BackHandler(enabled = settingsOpen || selectedId != null) {
        if (settingsOpen) settingsOpen = false else selectedId = null
    }

    UnifiedMessagesTheme(style = style) {
        val palette = LocalUnifiedPalette.current
        val animatedBackground by animateColorAsState(palette.background, tween(450), label = "Background color")
        val glassGlow by animateFloatAsState(
            if (style == VisualStyle.LIQUID_GLASS) 1f else 0f,
            tween(500),
            label = "Glass glow",
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (glassGlow > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.width.coerceAtLeast(1f) * 0.9f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF4A668F).copy(alpha = 0.27f * glassGlow), Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.08f),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(size.width * 0.92f, size.height * 0.08f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF9E4C4B).copy(alpha = 0.14f * glassGlow), Color.Transparent),
                            center = Offset(size.width * 0.06f, size.height * 0.94f),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(size.width * 0.06f, size.height * 0.94f),
                    )
                }
            }
            val expanded = maxWidth >= 840.dp
            if (settingsOpen) {
                SettingsScreen(
                    style = style,
                    onStyleSelected = { chosen ->
                        styleName = chosen.name
                        preferences.edit { putString("visual_style", chosen.name) }
                    },
                    onBack = { settingsOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    InboxScreen(
                        onConversationSelected = { selectedId = it },
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
                                onBack = { selectedId = null },
                                showBack = false,
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
                    val conversation = conversationId?.let(SampleConversationRepository::findById)
                    if (conversation == null) {
                        InboxScreen(
                            onConversationSelected = { selectedId = it },
                            onSettings = { settingsOpen = true },
                            selectedId = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ConversationScreen(
                            conversation = conversation,
                            onBack = { selectedId = null },
                            showBack = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationHint() {
    val palette = LocalUnifiedPalette.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            DotMatrixAccent(columns = 8, rows = 4, dotSize = 3.dp, dotSpacing = 9.dp)
            Text(
                text = "SELECT A CONVERSATION",
                style = MaterialTheme.typography.titleLarge,
                color = palette.primaryText,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = "Sample conversations are shown here. Messaging is not connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryText,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
