package com.unifiedmessages.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unifiedmessages.app.matrix.MatrixMessageActions
import com.unifiedmessages.app.ui.components.overlayPanelColor
import com.unifiedmessages.app.ui.components.overlayScrimColor
import com.unifiedmessages.app.ui.theme.LocalAppTheme

/** A capability-gated action panel; the conversation bubble and layout stay in place. */
@Composable
fun MatrixMessageActionDialog(
    actions: MatrixMessageActions,
    originalText: String,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: (String) -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalAppTheme.current
    var mode by remember { mutableStateOf("actions") }
    var editText by remember { mutableStateOf(originalText) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(overlayScrimColor())) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = onDismiss,
            ))
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp)
                    .widthIn(max = 560.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(overlayPanelColor()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(when (mode) { "edit" -> "Edit message"; "unsend" -> "Unsend message";
                    else -> "Message actions" },
                    style = MaterialTheme.typography.titleLarge, color = theme.onSurface)
                when (mode) {
                    "edit" -> {
                        OutlinedTextField(editText, onValueChange = { editText = it },
                            label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { mode = "actions" }) { Text("Cancel") }
                            Button(onClick = { onEdit(editText.trim()); onDismiss() },
                                enabled = editText.isNotBlank() && editText.trim() != originalText.trim(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accent, contentColor = theme.onAccent)) {
                                Text("Save")
                            }
                        }
                    }
                    "unsend" -> {
                        Text("This removes the message for everyone in the Matrix room. Your local copy is retained privately.",
                            color = theme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { mode = "actions" }) { Text("Cancel") }
                            Button(onClick = { onUnsend(); onDismiss() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accent, contentColor = theme.onAccent)) {
                                Text("Unsend")
                            }
                        }
                    }
                    else -> {
                        if (actions.react) {
                            Text("React", style = MaterialTheme.typography.labelMedium,
                                color = theme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("👍", "❤️", "😂").forEach { emoji ->
                                    TextButton(onClick = { onReact(emoji); onDismiss() }) {
                                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                        if (actions.edit) TextButton(onClick = { mode = "edit" }) { Text("Edit") }
                        if (actions.reply) TextButton(onClick = { onReply(); onDismiss() }) { Text("Reply") }
                        if (actions.unsend) TextButton(onClick = { mode = "unsend" }) { Text("Unsend") }
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
