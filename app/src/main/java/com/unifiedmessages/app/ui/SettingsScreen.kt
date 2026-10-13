package com.unifiedmessages.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.components.StyleSurface
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette
import com.unifiedmessages.app.ui.theme.VisualStyle

@Composable
fun SettingsScreen(
    style: VisualStyle,
    onStyleSelected: (VisualStyle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUnifiedPalette.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to messages", tint = palette.primaryText)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = palette.primaryText)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 5.dp, top = 14.dp)) {
            DotMatrixAccent(columns = 3, rows = 2, color = palette.accent)
            Text("  APPEARANCE", style = MaterialTheme.typography.labelMedium, color = palette.accent)
        }

        Text(
            "Choose a look for your inbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.secondaryText,
            modifier = Modifier.padding(start = 5.dp, top = 12.dp, bottom = 17.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceOption(
                title = "Chats · Default",
                description = "A calm light canvas, white cards, and electric-blue details.",
                selected = style == VisualStyle.CHATS,
                onClick = { onStyleSelected(VisualStyle.CHATS) },
            )
            AppearanceOption(
                title = "Liquid Glass",
                description = "A darker look with translucent layers and soft blue highlights.",
                selected = style == VisualStyle.LIQUID_GLASS,
                onClick = { onStyleSelected(VisualStyle.LIQUID_GLASS) },
            )
        }

        Text(
            "ABOUT THIS PREVIEW",
            style = MaterialTheme.typography.labelMedium,
            color = palette.accent,
            modifier = Modifier.padding(start = 5.dp, top = 35.dp, bottom = 12.dp),
        )
        StyleSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Column {
                Text("UnifiedMessages", style = MaterialTheme.typography.titleMedium, color = palette.primaryText)
                Text(
                    "Version 0.2 · interface preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "All conversations are fictional samples stored in the app. This version does not access contacts, read messages, connect to iMessage or RCS, or send SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Text(
            "Designed for Nothing Phone (2). Free to build and run.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryText,
            modifier = Modifier.padding(start = 5.dp, top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun AppearanceOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalUnifiedPalette.current
    StyleSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = palette.primaryText)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
