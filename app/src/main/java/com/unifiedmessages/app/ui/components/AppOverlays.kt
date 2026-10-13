package com.unifiedmessages.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle

/** Popups live in their own window, where the screen's Haze backdrop cannot be sampled. */
@Composable
fun overlayPanelColor(): Color {
    val theme = LocalAppTheme.current
    return theme.surface.compositeOver(theme.background).copy(alpha = 0.96f)
}

@Composable
fun overlayScrimColor(): Color =
    Color.Black.copy(alpha = if (LocalAppTheme.current.isDark) 0.54f else 0.42f)

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = overlayPanelColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** The opaque panel and explicit scrim keep text readable in both glass palettes. */
@Composable
fun AppPreviewOnlyDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
) {
    val scrim = overlayScrimColor()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(scrim)) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close dialog",
                    onClick = onDismissRequest,
                ),
            )
            AppPreviewOnlyPanel(
                onDismissRequest = onDismissRequest,
                title = title,
                message = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

/** The same panel can be inspected in Compose Preview without a separate Dialog window. */
@Composable
fun AppPreviewOnlyPanel(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val specular = if (theme.style == SurfaceStyle.LIQUID_GLASS) {
        Modifier.background(
            Brush.verticalGradient(
                0f to theme.glass.highlight.copy(alpha = 0.09f),
                0.14f to theme.glass.highlight.copy(alpha = 0.055f),
                0.38f to theme.glass.highlight.copy(alpha = 0.02f),
                0.7f to theme.glass.highlight.copy(alpha = 0.005f),
                1f to Color.Transparent,
            ),
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(overlayPanelColor())
            .then(specular)
            .semantics { paneTitle = title }
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = theme.onSurface)
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = theme.onAccent,
                ),
            ) {
                Text("Got it")
            }
        }
    }
}
