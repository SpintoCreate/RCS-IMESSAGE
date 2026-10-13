package com.unifiedmessages.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle

/** Kept for existing callers while screens move to the semantic AppCard wrapper. */
@Composable
fun StyleSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    AppCard(
        modifier = modifier,
        shape = shape,
        color = LocalAppTheme.current.surface,
        contentPadding = contentPadding,
        content = content,
    )
}

/** Compact transport label with readable text and an explicit spoken source. */
@Composable
fun SourceBadge(
    source: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val label = when (source.trim().uppercase()) {
        "IMESSAGE" -> "iMessage"
        "RCS" -> "RCS"
        "SMS" -> "SMS"
        else -> source.trim()
    }
    val tint = when (label) {
        "iMessage" -> theme.imessage
        "RCS" -> theme.rcs
        "SMS" -> theme.sms
        else -> theme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = if (theme.style == SurfaceStyle.SOLID) 0.1f else 0.16f))
            .semantics(mergeDescendants = true) { contentDescription = "Source: $label" }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private val avatarSwatches = listOf(
    Color(0xFF142B68) to Color(0xFF536AAB),
    Color(0xFF0B524D) to Color(0xFF3A746E),
    Color(0xFF563164) to Color(0xFF80608C),
    Color(0xFF7C391B) to Color(0xFFA15C39),
    Color(0xFF30435E) to Color(0xFF5B718D),
)

/** Fictional initials on a stable abstract gradient; no contact photo permission is needed. */
@Composable
fun ContactAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val initials = text.trim().uppercase().take(3).ifEmpty { "?" }
    val (start, end) = avatarSwatches[Math.floorMod(initials.hashCode(), avatarSwatches.size)]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end)))
            .clearAndSetSemantics { contentDescription = "Avatar with initials $initials" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(size * 0.63f)
                .background(Color.White.copy(alpha = 0.12f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size * 0.28f)
                .background(Color.Black.copy(alpha = 0.08f), CircleShape),
        )
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small decorative dot grid; hidden from accessibility services. */
@Composable
fun DotMatrixAccent(
    modifier: Modifier = Modifier,
    columns: Int = 4,
    rows: Int = 2,
    dotSize: Dp = 2.dp,
    dotSpacing: Dp = 5.dp,
    color: Color = LocalAppTheme.current.onSurfaceVariant.copy(alpha = 0.45f),
) {
    Column(
        modifier = modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(dotSpacing),
    ) {
        repeat(rows.coerceAtLeast(0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dotSpacing)) {
                repeat(columns.coerceAtLeast(0)) {
                    Spacer(
                        Modifier
                            .size(dotSize)
                            .background(color, CircleShape),
                    )
                }
            }
        }
    }
}
