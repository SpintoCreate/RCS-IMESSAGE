package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.ui.components.AppBackdrop
import com.unifiedmessages.app.ui.components.AppPreviewOnlyPanel
import com.unifiedmessages.app.ui.components.overlayScrimColor
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import com.unifiedmessages.app.ui.theme.UnifiedMessagesTheme

/** Android Studio renders each screen in all four appearances with blue and lime accents. */
class AppearancePreviewVariants : PreviewParameterProvider<AppearanceSettings> {
    override val values: Sequence<AppearanceSettings> = sequence {
        for (style in SurfaceStyle.entries) {
            for (brightness in listOf(Brightness.LIGHT, Brightness.DARK)) {
                for (accent in listOf(0xFF0735FF.toInt(), 0xFF6F8700.toInt())) {
                    yield(AppearanceSettings(style = style, brightness = brightness, accentArgb = accent))
                }
            }
        }
    }
}

@Composable
private fun AppearancePreviewFrame(
    settings: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    UnifiedMessagesTheme(settings = settings) {
        val theme = LocalAppTheme.current
        Box(Modifier.fillMaxSize().background(theme.background)) {
            AppBackdrop(Modifier.matchParentSize())
            content()
        }
    }
}

@Preview(name = "Welcome · blue and lime", widthDp = 393, heightDp = 873)
@Composable
private fun WelcomeAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        WelcomeScreen(onContinue = {}, modifier = Modifier.fillMaxSize())
    }
}

@Preview(name = "Chats · blue and lime", widthDp = 393, heightDp = 873)
@Composable
private fun InboxAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        InboxScreen(
            conversations = SampleConversationRepository.conversations,
            query = "",
            onQueryChange = {},
            source = null,
            onSourceChange = {},
            onConversationSelected = {},
            onPinToggle = { _, _ -> },
            onNewMessage = {},
            canStartNewMessage = false,
            onSettings = {},
            selectedId = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Conversation · blue and lime", widthDp = 393, heightDp = 873)
@Composable
private fun ConversationAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        ConversationScreen(
            conversation = SampleConversationRepository.conversations.first(),
            searchQuery = "",
            onSearchQueryChange = {},
            canSend = false,
            onSend = { _, complete -> complete(true) },
            onBack = {},
            showBack = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Settings · blue and lime", widthDp = 393, heightDp = 873)
@Composable
private fun SettingsAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        SettingsScreen(
            settings = settings,
            onStyleChange = {},
            onBrightnessChange = {},
            onAccentChange = {},
            showSenderDeletedMessages = true,
            onShowSenderDeletedMessagesChange = {},
            onBack = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Dialog · blue and lime", widthDp = 393, heightDp = 873)
@Composable
private fun DialogAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        Box(
            modifier = Modifier.fillMaxSize().background(overlayScrimColor()),
            contentAlignment = Alignment.Center,
        ) {
            AppPreviewOnlyPanel(
                onDismissRequest = {},
                title = "SMS unavailable",
                message = "Set UnifiedMessages as your default SMS app to start a conversation.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            )
        }
    }
}
