package com.unifiedmessages.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unifiedmessages.app.ui.theme.LocalAppTheme
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Shared backdrop capture for surfaces; provide one state above each screen's background layer. */
val LocalAppHazeState = compositionLocalOf<HazeState?> { null }

/** Capture a background that a glass surface can blur. Avoid putting this on a parent of the glass. */
@Composable
fun Modifier.appHazeSource(): Modifier {
    val state = LocalAppHazeState.current
    val theme = LocalAppTheme.current
    return if (theme.style == SurfaceStyle.LIQUID_GLASS && state != null) {
        this.hazeSource(state)
    } else {
        this
    }
}

/** A subtle wallpaper-like gradient behind translucent panels and message bubbles. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val glass = theme.style == SurfaceStyle.LIQUID_GLASS
    val base = if (glass) {
        if (theme.isDark) {
            Brush.verticalGradient(
                listOf(theme.background, theme.accent.copy(alpha = 0.42f), theme.background),
            )
        } else {
            Brush.verticalGradient(
                listOf(theme.background, theme.accent.copy(alpha = 0.25f), theme.background),
            )
        }
    } else {
        Brush.verticalGradient(listOf(theme.background, theme.background))
    }
    Box(modifier = modifier.appHazeSource().background(base)) {
        if (glass) {
            val glow = if (theme.isDark) 0.4f else 0.22f
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(theme.accent.copy(alpha = glow), Color.Transparent),
                            radius = 900f,
                        ),
                    ),
            )
        }
    }
}

private enum class SurfaceRole { DEFAULT, CARD, INCOMING, OUTGOING, FAB }

/** The visual treatment is applied inside the caller's modifier, preserving screen layout. */
@Composable
private fun SurfaceLayer(
    modifier: Modifier,
    shape: Shape,
    color: Color?,
    contentPadding: PaddingValues,
    role: SurfaceRole,
    backdropColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val hazeState = LocalAppHazeState.current
    val base = color ?: when (role) {
        SurfaceRole.DEFAULT -> theme.surface
        SurfaceRole.CARD -> theme.surface
        SurfaceRole.INCOMING -> theme.bubbleIncoming
        SurfaceRole.OUTGOING -> theme.bubbleOutgoing
        SurfaceRole.FAB -> theme.accent
    }
    val contentColor = when (role) {
        SurfaceRole.INCOMING -> theme.onBubbleIncoming
        SurfaceRole.OUTGOING -> theme.onBubbleOutgoing
        SurfaceRole.FAB -> theme.onAccent
        else -> theme.onSurface
    }
    val layer = if (theme.style == SurfaceStyle.SOLID) {
        Modifier
            .background(base)
            .border(0.7.dp, theme.outline.copy(alpha = 0.4f), shape)
    } else {
        val tint = if (role == SurfaceRole.FAB) theme.accent else base
        val frosted = if (hazeState != null) {
            Modifier.hazeEffect(
                hazeState,
                style = HazeStyle(
                    backgroundColor = backdropColor ?: theme.background,
                    tint = HazeTint(tint.copy(alpha = theme.glass.tintAlpha)),
                    blurRadius = theme.glass.blurRadius,
                    fallbackTint = HazeTint(tint.copy(alpha = 0.88f)),
                ),
            ) { blurEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        } else {
            // Previews can render without a HazeState. Older devices use Haze's opaque scrim.
            Modifier.background(tint.copy(alpha = 0.88f))
        }
        frosted
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(theme.glass.highlight.copy(alpha = 0.13f), Color.Transparent),
                    ),
                    size = Size(size.width, size.height * 0.36f),
                )
                drawLine(
                    color = theme.glass.highlight.copy(alpha = 0.34f),
                    start = Offset(size.width * 0.15f, 1.dp.toPx()),
                    end = Offset(size.width * 0.85f, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .border(0.9.dp, Brush.verticalGradient(theme.glass.borderGradient), shape)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .clip(shape)
                .then(layer)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backdropColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) = SurfaceLayer(modifier, shape, color, contentPadding, SurfaceRole.DEFAULT, backdropColor, content)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) = SurfaceLayer(modifier, shape, color, contentPadding, SurfaceRole.CARD, content = content)

@Composable
fun AppBubble(
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) = SurfaceLayer(
    modifier, shape, null, contentPadding,
    if (outgoing) SurfaceRole.OUTGOING else SurfaceRole.INCOMING,
    content = content,
)

@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    color: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) = SurfaceLayer(modifier, shape, color, contentPadding, SurfaceRole.DEFAULT, content = content)

@Composable
fun AppInputBar(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    color: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) = SurfaceLayer(modifier, shape, color, contentPadding, SurfaceRole.DEFAULT, content = content)

@Composable
fun AppFab(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = if (onClick != null) {
        Modifier.clickable(onClickLabel = contentDescription, onClick = onClick)
    } else {
        Modifier.semantics { disabled() }
    }
    val accessible = if (contentDescription != null) {
        interaction.semantics { this.contentDescription = contentDescription }
    } else {
        interaction
    }
    SurfaceLayer(modifier.clip(shape).then(accessible), shape, null, contentPadding, SurfaceRole.FAB, content = content)
}
