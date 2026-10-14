package com.unifiedmessages.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unifiedmessages.app.ui.UnifiedMessagesApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var sharedAttachments by mutableStateOf<List<Uri>>(emptyList())
    private var requestedRecipient by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExternalIntent(intent)
        setContent {
            UnifiedMessagesApp(
                sharedAttachments = sharedAttachments,
                requestedRecipient = requestedRecipient,
                onShareHandled = { sharedAttachments = emptyList() },
                onRecipientHandled = { requestedRecipient = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExternalIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun readExternalIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                sharedAttachments = listOfNotNull(
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
                )
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                sharedAttachments = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            Intent.ACTION_SENDTO -> {
                requestedRecipient = intent.data?.schemeSpecificPart?.substringBefore('?')
                    ?.takeIf { it.isNotBlank() }
            }
        }
    }
}
