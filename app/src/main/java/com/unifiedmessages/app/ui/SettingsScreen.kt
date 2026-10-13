package com.unifiedmessages.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.components.DotMatrixAccent
import com.unifiedmessages.app.ui.components.AppBubble
import com.unifiedmessages.app.ui.components.AppFab
import com.unifiedmessages.app.ui.components.AppInputBar
import com.unifiedmessages.app.ui.components.StyleSurface
import com.unifiedmessages.app.ui.theme.AccentPresets
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onStyleChange: (SurfaceStyle) -> Unit,
    onBrightnessChange: (Brightness) -> Unit,
    onAccentChange: (Int) -> Unit,
    showSenderDeletedMessages: Boolean,
    onShowSenderDeletedMessagesChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var customPickerExpanded by rememberSaveable {
        mutableStateOf(AccentPresets.none { it.argb == settings.accentArgb })
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to messages", tint = theme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = theme.onSurface)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 5.dp, top = 14.dp)) {
            DotMatrixAccent(columns = 3, rows = 2, color = theme.accent)
            Text("  APPEARANCE", style = MaterialTheme.typography.labelMedium, color = theme.accent)
        }
        Text(
            "Make chats feel like yours. Changes apply immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp, top = 12.dp, bottom = 17.dp),
        )

        AppearanceHeading("SURFACE STYLE")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceOption(
                title = "Solid",
                description = "The original Chats look with clean, opaque cards.",
                selected = settings.style == SurfaceStyle.SOLID,
                onClick = { onStyleChange(SurfaceStyle.SOLID) },
            )
            AppearanceOption(
                title = "Liquid Glass",
                description = "Translucent layers over a softly colored background.",
                selected = settings.style == SurfaceStyle.LIQUID_GLASS,
                onClick = { onStyleChange(SurfaceStyle.LIQUID_GLASS) },
            )
        }

        AppearanceHeading("BRIGHTNESS", modifier = Modifier.padding(top = 26.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Brightness.entries.forEach { brightness ->
                val label = brightness.name.lowercase().replaceFirstChar { it.titlecase() }
                FilterChip(
                    selected = settings.brightness == brightness,
                    onClick = { onBrightnessChange(brightness) },
                    label = { Text(label) },
                    modifier = Modifier.semantics {
                        contentDescription = "$label brightness"
                        selected = settings.brightness == brightness
                    },
                )
            }
        }

        AppearanceHeading("ACCENT COLOR", modifier = Modifier.padding(top = 24.dp))
        Text(
            "Used for active controls, the compose button, and outgoing messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentPresets.forEach { preset ->
                AccentChoice(
                    label = preset.name,
                    color = Color(preset.argb),
                    selected = settings.accentArgb == preset.argb && !customPickerExpanded,
                    onClick = {
                        customPickerExpanded = false
                        onAccentChange(preset.argb)
                    },
                )
            }
            AccentChoice(
                label = "Custom",
                color = Color(settings.accentArgb),
                selected = customPickerExpanded,
                onClick = { customPickerExpanded = true },
                custom = true,
            )
        }
        if (customPickerExpanded) {
            CustomAccentPicker(
                argb = settings.accentArgb,
                onAccentChange = onAccentChange,
                modifier = Modifier.padding(top = 17.dp),
            )
        }

        AppearanceHeading("LIVE PREVIEW", modifier = Modifier.padding(top = 27.dp))
        AppearancePreview(modifier = Modifier.fillMaxWidth())

        AppearanceHeading("MESSAGE DISPLAY", modifier = Modifier.padding(top = 35.dp))
        StyleSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show sender deletions", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                    Text(
                        "Keep a 'Deleted by sender' marker in the conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Switch(
                    checked = showSenderDeletedMessages,
                    onCheckedChange = onShowSenderDeletedMessagesChange,
                    modifier = Modifier.semantics { contentDescription = "Show messages deleted by sender" },
                )
            }
        }

        AppearanceHeading("ABOUT THIS PREVIEW", modifier = Modifier.padding(top = 35.dp))
        StyleSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Column {
                Text("UnifiedMessages", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Text(
                    "Interface preview · sample messages",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "All conversations are fictional samples stored in the app. This version does not access contacts, read messages, connect to iMessage or RCS, or send SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Text(
            "Designed for Nothing Phone (2). Free to build and run.",
            style = MaterialTheme.typography.bodySmall,
            color = theme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp, top = 18.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun AppearanceHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalAppTheme.current.accent,
        modifier = modifier.padding(start = 5.dp, bottom = 12.dp),
    )
}

@Composable
private fun AppearanceOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    StyleSurface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun AccentChoice(label: String, color: Color, selected: Boolean, onClick: () -> Unit, custom: Boolean = false) {
    val theme = LocalAppTheme.current
    val iconTint = if (color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) theme.onSurface else theme.outline, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(color)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = "$label accent" },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            } else if (custom) {
                Text("+", color = iconTint, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = theme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun CustomAccentPicker(argb: Int, onAccentChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    fun update(index: Int, value: Float) {
        val adjusted = hsv.copyOf()
        adjusted[index] = value
        onAccentChange(AndroidColor.HSVToColor(adjusted))
    }

    StyleSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(argb)))
                Text(
                    "Custom · #${"%06X".format(argb and 0xFFFFFF)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                "Choose any color with the three controls below.",
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceVariant,
                modifier = Modifier.padding(top = 9.dp, bottom = 8.dp),
            )
            ColorSlider("Hue", hsv[0], 0f..360f) { update(0, it) }
            ColorSlider("Saturation", hsv[1], 0f..1f) { update(1, it) }
            ColorSlider("Brightness", hsv[2], 0f..1f) { update(2, it) }
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val theme = LocalAppTheme.current
    Text(label, style = MaterialTheme.typography.bodyMedium, color = theme.onSurface)
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label for custom accent" },
    )
}

@Composable
private fun AppearancePreview(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    StyleSurface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alex Rivera", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("RCS", style = MaterialTheme.typography.labelSmall, color = theme.accent)
            }
            Spacer(Modifier.height(15.dp))
            AppBubble(
                outgoing = false,
                shape = RoundedCornerShape(15.dp, 15.dp, 15.dp, 5.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text("See you soon!", color = theme.onBubbleIncoming, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            AppBubble(
                outgoing = true,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(15.dp, 15.dp, 5.dp, 15.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text("On my way!", color = theme.onBubbleOutgoing, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppInputBar(
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = CircleShape,
                    color = theme.surfaceVariant,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(
                        "Message preview",
                        color = theme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                Spacer(Modifier.width(8.dp))
                AppFab(modifier = Modifier.size(42.dp)) {
                    Text("+", color = theme.onAccent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
