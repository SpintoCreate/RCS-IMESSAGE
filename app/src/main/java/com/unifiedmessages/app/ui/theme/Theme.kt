package com.unifiedmessages.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The two visual treatments available in Settings. Both use a dark, accessible color scheme. */
enum class VisualStyle { NOTHING, LIQUID_GLASS }

@Immutable
data class UnifiedPalette(
    val style: VisualStyle,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val outline: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val incomingBubble: Color,
    val outgoingBubble: Color,
    val onIncomingBubble: Color,
    val onOutgoingBubble: Color,
    val imessage: Color,
    val rcs: Color,
    val sms: Color,
)

private val NothingPalette = UnifiedPalette(
    style = VisualStyle.NOTHING,
    background = Color(0xFF050506),
    surface = Color(0xFF151517),
    surfaceElevated = Color(0xFF202023),
    surfaceHighlight = Color(0xFF29292D),
    outline = Color(0xFF38383C),
    primaryText = Color(0xFFF7F7F5),
    secondaryText = Color(0xFFB8B8BB),
    accent = Color(0xFFFF625C),
    incomingBubble = Color(0xFF252528),
    outgoingBubble = Color(0xFF156FE8),
    onIncomingBubble = Color(0xFFF7F7F5),
    onOutgoingBubble = Color.White,
    imessage = Color(0xFF82B8FF),
    rcs = Color(0xFF80D5AF),
    sms = Color(0xFFD2D2D4),
)

private val LiquidGlassPalette = UnifiedPalette(
    style = VisualStyle.LIQUID_GLASS,
    background = Color(0xFF080B12),
    surface = Color(0xFF171B26),
    surfaceElevated = Color(0xFF242A38),
    surfaceHighlight = Color(0xFF343D50),
    outline = Color(0xFF596273),
    primaryText = Color(0xFFF9FAFC),
    secondaryText = Color(0xFFBBC3D2),
    accent = Color(0xFFFF766F),
    incomingBubble = Color(0xFF303746),
    outgoingBubble = Color(0xFF3278D9),
    onIncomingBubble = Color(0xFFF9FAFC),
    onOutgoingBubble = Color.White,
    imessage = Color(0xFF9CC7FF),
    rcs = Color(0xFF94DEBE),
    sms = Color(0xFFDADEE8),
)

val LocalUnifiedPalette = staticCompositionLocalOf { NothingPalette }

private val UnifiedTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun UnifiedMessagesTheme(
    style: VisualStyle = VisualStyle.NOTHING,
    content: @Composable () -> Unit,
) {
    val palette = if (style == VisualStyle.LIQUID_GLASS) LiquidGlassPalette else NothingPalette
    val colors = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF1C0807),
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.primaryText,
        secondary = palette.imessage,
        onSecondary = Color(0xFF081728),
        background = palette.background,
        onBackground = palette.primaryText,
        surface = palette.surface,
        onSurface = palette.primaryText,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.secondaryText,
        surfaceDim = palette.background,
        surfaceBright = palette.surfaceHighlight,
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceElevated,
        surfaceContainerHighest = palette.surfaceHighlight,
        outline = palette.outline,
        outlineVariant = palette.outline.copy(alpha = 0.7f),
        inverseSurface = palette.primaryText,
        inverseOnSurface = palette.background,
    )

    CompositionLocalProvider(LocalUnifiedPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = UnifiedTypography, content = content)
    }
}
