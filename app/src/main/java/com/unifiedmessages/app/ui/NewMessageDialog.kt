package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unifiedmessages.app.ui.components.overlayPanelColor
import com.unifiedmessages.app.ui.components.overlayScrimColor
import com.unifiedmessages.app.ui.theme.LocalAppTheme

/** A recipient prompt using the app's existing opaque overlay treatment. */
@Composable
fun NewMessageDialog(
    onDismiss: () -> Unit,
    onCreate: (String, (Boolean) -> Unit) -> Unit,
    attachmentCount: Int = 0,
) {
    val theme = LocalAppTheme.current
    var address by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val scrim = overlayScrimColor()
    Dialog(onDismissRequest = { if (!submitting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scrim)) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!submitting) onDismiss() },
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp)
                    .widthIn(max = 560.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(overlayPanelColor())
                    .semantics { paneTitle = if (attachmentCount > 0) "Send MMS" else "New message" }
                    .padding(24.dp),
            ) {
                Text(
                    if (attachmentCount > 0) "Send MMS" else "New message",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.onSurface,
                )
                if (attachmentCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$attachmentCount media ${if (attachmentCount == 1) "attachment" else "attachments"} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceVariant,
                        unfocusedContainerColor = theme.surfaceVariant,
                        focusedTextColor = theme.onSurface,
                        unfocusedTextColor = theme.onSurface,
                        focusedLabelColor = theme.onSurfaceVariant,
                        unfocusedLabelColor = theme.onSurfaceVariant,
                        cursorColor = theme.accent,
                        focusedIndicatorColor = theme.accent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel", color = theme.onSurface) }
                    Button(
                        onClick = {
                            submitting = true
                            onCreate(address.trim()) { submitting = false }
                        },
                        enabled = address.isNotBlank() && !submitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.accent,
                            contentColor = theme.onAccent,
                        ),
                    ) { Text(if (attachmentCount > 0) "Send" else "Continue") }
                }
            }
        }
    }
}
