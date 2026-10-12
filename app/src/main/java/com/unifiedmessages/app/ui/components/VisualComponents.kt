package com.unifiedmessages.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.theme.LocalUnifiedPalette
import com.unifiedmessages.app.ui.theme.VisualStyle

/** A card that follows the selected appearance without blurring its text or controls. */
@Composable
fun StyleSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalUnifiedPalette.current
    val background = if (palette.style == VisualStyle.LIQUID_GLASS) {
        Brush.linearGradient(
            listOf(
                palette.surfaceHighlight.copy(alpha = 0.92f),
                palette.surfaceElevated.copy(alpha = 0.86f),
                palette.surface.copy(alpha = 0.92f),
            ),
        )
    } else {
        Brush.linearGradient(listOf(palette.surface, palette.surface))
    }
    val stroke = if (palette.style == VisualStyle.LIQUID_GLASS) {
        palette.outline.copy(alpha = 0.72f)
    } else {
        palette.outline.copy(alpha = 0.55f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, stroke, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Compact transport label; color and spoken label both identify the source. */
@Composable
fun SourceBadge(
    source: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalUnifiedPalette.current
    val label = when (source.trim().uppercase()) {
        "IMESSAGE" -> "iMessage"
        "RCS" -> "RCS"
        "SMS" -> "SMS"
        else -> source.trim()
    }
    val tint = when (label) {
        "iMessage" -> palette.imessage
        "RCS" -> palette.rcs
        "SMS" -> palette.sms
        else -> palette.secondaryText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.48f), RoundedCornerShape(7.dp))
            .semantics(mergeDescendants = true) { contentDescription = "Source: $label" }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
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
    color: Color = LocalUnifiedPalette.current.secondaryText.copy(alpha = 0.45f),
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
