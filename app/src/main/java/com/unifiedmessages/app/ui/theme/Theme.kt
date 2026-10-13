package com.unifiedmessages.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Chats is the light appearance shown by default; Liquid Glass is an optional dark appearance. */
enum class VisualStyle { CHATS, LIQUID_GLASS }

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

private val ChatsPalette = UnifiedPalette(
    style = VisualStyle.CHATS,
    background = Color(0xFFF1F1F1),
    surface = Color.White,
    surfaceElevated = Color(0xFFFAFAFA),
    surfaceHighlight = Color(0xFFE9E9ED),
    outline = Color(0xFFDEDFE2),
    primaryText = Color(0xFF111113),
    secondaryText = Color(0xFF5D6167),
    accent = Color(0xFF0735FF),
    incomingBubble = Color.White,
    outgoingBubble = Color(0xFFD3D5D8),
    onIncomingBubble = Color(0xFF111113),
    onOutgoingBubble = Color(0xFF111113),
    imessage = Color(0xFF2143B0),
    rcs = Color(0xFF00694F),
    sms = Color(0xFF4F545C),
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
    accent = Color(0xFF98AAFF),
    incomingBubble = Color(0xFF303746),
    outgoingBubble = Color(0xFF294DAF),
    onIncomingBubble = Color(0xFFF9FAFC),
    onOutgoingBubble = Color.White,
    imessage = Color(0xFFA8BDFF),
    rcs = Color(0xFF94DEBE),
    sms = Color(0xFFDADEE8),
)

val LocalUnifiedPalette = staticCompositionLocalOf { ChatsPalette }

private val UnifiedTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 43.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

@Composable
fun UnifiedMessagesTheme(
    style: VisualStyle = VisualStyle.CHATS,
    content: @Composable () -> Unit,
) {
    val palette = if (style == VisualStyle.LIQUID_GLASS) LiquidGlassPalette else ChatsPalette
    val colors = if (style == VisualStyle.CHATS) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9EDFF),
            onPrimaryContainer = Color(0xFF132686),
            secondary = palette.imessage,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.primaryText,
            surface = palette.surface,
            onSurface = palette.primaryText,
            surfaceVariant = palette.surfaceHighlight,
            onSurfaceVariant = palette.secondaryText,
            surfaceDim = palette.background,
            surfaceBright = palette.surface,
            surfaceContainerLowest = palette.surface,
            surfaceContainerLow = palette.surfaceElevated,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHighlight,
            surfaceContainerHighest = palette.surfaceHighlight,
            outline = palette.outline,
            outlineVariant = palette.outline,
            inverseSurface = palette.primaryText,
            inverseOnSurface = palette.surface,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF101C58),
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
    }

    CompositionLocalProvider(LocalUnifiedPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = UnifiedTypography, content = content)
    }
}
