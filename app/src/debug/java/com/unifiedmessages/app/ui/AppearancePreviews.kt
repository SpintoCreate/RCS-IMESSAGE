package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.unifiedmessages.app.data.SampleConversationRepository
import com.unifiedmessages.app.ui.components.AppBackdrop
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import com.unifiedmessages.app.ui.theme.UnifiedMessagesTheme

/** Android Studio renders each screen once for every surface and brightness combination. */
class AppearancePreviewVariants : PreviewParameterProvider<AppearanceSettings> {
    override val values = sequenceOf(
        AppearanceSettings(style = SurfaceStyle.SOLID, brightness = Brightness.LIGHT),
        AppearanceSettings(style = SurfaceStyle.SOLID, brightness = Brightness.DARK),
        AppearanceSettings(style = SurfaceStyle.LIQUID_GLASS, brightness = Brightness.LIGHT),
        AppearanceSettings(style = SurfaceStyle.LIQUID_GLASS, brightness = Brightness.DARK),
    )
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

@Preview(name = "Welcome · four appearances", widthDp = 393, heightDp = 873)
@Composable
private fun WelcomeAppearancePreview(
    @PreviewParameter(AppearancePreviewVariants::class) settings: AppearanceSettings,
) {
    AppearancePreviewFrame(settings) {
        WelcomeScreen(onContinue = {}, modifier = Modifier.fillMaxSize())
    }
}

@Preview(name = "Chats · four appearances", widthDp = 393, heightDp = 873)
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

@Preview(name = "Conversation · four appearances", widthDp = 393, heightDp = 873)
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
            onSend = {},
            onBack = {},
            showBack = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
