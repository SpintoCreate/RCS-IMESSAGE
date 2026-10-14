package com.unifiedmessages.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.theme.LocalAppTheme

/** Keeps SMS setup visible without blocking the message composer. */
@Composable
fun SmsAccessBanner(message: String, onRequestSmsAccess: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = theme.surfaceVariant,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestSmsAccess) {
                Text("Enable SMS", color = theme.accent)
            }
        }
    }
}
