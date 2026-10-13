package com.unifiedmessages.app.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.content.edit
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.unifiedmessages.app.data.AppearanceRepository
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.ui.components.AppBackdrop
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.components.LocalAppHazeState
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import com.unifiedmessages.app.ui.theme.UnifiedMessagesTheme
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun UnifiedMessagesApp() {
    val context = LocalContext.current
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
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val selected = selectedId?.let(SampleConversationRepository::findById)

    BackHandler(enabled = settingsOpen || selectedId != null) {
        if (settingsOpen) settingsOpen = false else selectedId = null
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
                            },
                            modifier = Modifier.fillMaxSize(),
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
                text = "Your sample conversation will appear here. Messaging is not connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
