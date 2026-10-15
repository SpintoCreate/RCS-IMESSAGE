package com.unifiedmessages.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unifiedmessages.app.model.ChatAttachment
import com.unifiedmessages.app.matrix.MatrixMediaRef
import com.unifiedmessages.app.matrix.MatrixProvider
import com.unifiedmessages.app.ui.components.overlayPanelColor
import com.unifiedmessages.app.ui.components.overlayScrimColor
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MatrixMediaEntryPoint { fun matrixProvider(): MatrixProvider }

/** Opens imported MMS parts with an installed media viewer, without changing chat layout. */
@Composable
fun MessageAttachmentsDialog(attachments: List<ChatAttachment>, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val matrixProvider = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, MatrixMediaEntryPoint::class.java)
            .matrixProvider()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(overlayScrimColor())) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            )
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 28.dp)
                    .widthIn(max = 560.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(overlayPanelColor())
                    .semantics { paneTitle = "Media attachments" }.padding(24.dp),
            ) {
                Text("Media attachments", style = MaterialTheme.typography.titleLarge, color = theme.onSurface)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    attachments.forEachIndexed { index, attachment ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val reference = MatrixMediaRef.parse(attachment.uri)
                                        val uri = if (reference != null) {
                                            matrixProvider.openMedia(reference, attachment.fileName, attachment.mimeType)
                                        } else Uri.parse(attachment.uri)
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, attachment.mimeType)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        )
                                        onDismiss()
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "No app can open this attachment", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "This attachment is unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = attachment.uri.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                attachment.fileName?.takeIf(String::isNotBlank)
                                    ?: "Attachment ${index + 1} · ${attachment.mimeType}",
                                color = theme.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
