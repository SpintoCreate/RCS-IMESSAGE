package com.unifiedmessages.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SurfaceStyle { SOLID, LIQUID_GLASS }

enum class Brightness { LIGHT, DARK, SYSTEM }

@Immutable
data class AppearanceSettings(
    val style: SurfaceStyle = SurfaceStyle.SOLID,
    val brightness: Brightness = Brightness.LIGHT,
    val accentArgb: Int = 0xFF0735FF.toInt(),
) {
    val accent: Color get() = Color(normalizeAccentArgb(accentArgb))
}

@Immutable
data class AccentPreset(val name: String, val argb: Int)

/** Ten free, built-in choices. The first is the existing Chats blue. */
val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("Electric Blue", 0xFF0735FF.toInt()),
    AccentPreset("Cobalt", 0xFF2453D8.toInt()),
    AccentPreset("Sky", 0xFF087FCB.toInt()),
    AccentPreset("Teal", 0xFF008F85.toInt()),
    AccentPreset("Emerald", 0xFF1F8A5B.toInt()),
    AccentPreset("Lime", 0xFF6F8700.toInt()),
    AccentPreset("Amber", 0xFFB66A00.toInt()),
    AccentPreset("Coral", 0xFFC95938.toInt()),
    AccentPreset("Red", 0xFFC82445.toInt()),
    AccentPreset("Violet", 0xFF713AC4.toInt()),
)

/** Custom colors are stored as opaque ARGB; an unset zero falls back to the default blue. */
fun normalizeAccentArgb(argb: Int): Int =
    if (argb == 0) AccentPresets.first().argb else argb or 0xFF000000.toInt()

data class GlassTokens(
    val blurRadius: Dp,
    val tintAlpha: Float,
    val borderGradient: List<Color>,
    val highlight: Color,
)

/** Semantic colors shared by every screen and surface wrapper. */
data class AppTheme(
    val style: SurfaceStyle,
    val brightness: Brightness,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val bubbleIncoming: Color,
    val bubbleOutgoing: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onBubbleIncoming: Color,
    val onBubbleOutgoing: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val selectedSurface: Color,
    val imessage: Color,
    val rcs: Color,
    val sms: Color,
    val glass: GlassTokens,
    // The welcome artwork stays dark in every palette for its original contrast and layout.
    val welcomeBackground: Color = Color(0xFF05080F),
    val welcomeOnBackground: Color = Color.White,
    val welcomeSecondary: Color = Color(0xFFD5DDEB),
    val welcomeSurface: Color = if (style == SurfaceStyle.SOLID) Color(0xFF242B36) else Color.White.copy(alpha = 0.12f),
    val welcomeActionSurface: Color = Color.White,
    val welcomeOnActionSurface: Color = Color(0xFF0B1019),
    val welcomeGlowPrimary: Color = if (accent == Color(0xFF0735FF)) Color(0xFF5582BD)
        else lerp(Color(0xFF5582BD), accent, 0.35f),
    val welcomeGlowSecondary: Color = if (accent == Color(0xFF0735FF)) Color(0xFF264771)
        else lerp(Color(0xFF264771), accent, 0.15f),
)

private val DefaultTheme = resolveAppTheme(AppearanceSettings(), systemDark = false)

val LocalAppTheme = staticCompositionLocalOf { DefaultTheme }

/** Resolves independent settings into one palette; safe to call from previews and unit tests. */
fun resolveAppTheme(settings: AppearanceSettings, systemDark: Boolean): AppTheme {
    val isDark = when (settings.brightness) {
        Brightness.LIGHT -> false
        Brightness.DARK -> true
        Brightness.SYSTEM -> systemDark
    }
    val accent = settings.accent
    val defaultAccent = normalizeAccentArgb(settings.accentArgb) == AccentPresets.first().argb
    val onAccent = if (accent.luminance() > 0.18f) Color.Black else Color.White
    val glass = settings.style == SurfaceStyle.LIQUID_GLASS

    val glassTokens = if (glass) {
        if (isDark) {
            GlassTokens(
                blurRadius = 22.dp,
                tintAlpha = 0.56f,
                borderGradient = listOf(
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.22f),
                    accent.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.09f),
                ),
                highlight = Color.White.copy(alpha = 0.18f),
            )
        } else {
            GlassTokens(
                blurRadius = 22.dp,
                tintAlpha = 0.52f,
                borderGradient = listOf(
                    Color.White.copy(alpha = 0.96f),
                    Color.White.copy(alpha = 0.65f),
                    accent.copy(alpha = 0.17f),
                    Color.White.copy(alpha = 0.40f),
                ),
                highlight = Color.White.copy(alpha = 0.52f),
            )
        }
    } else {
        GlassTokens(0.dp, 1f, emptyList(), Color.Transparent)
    }

    return when {
        !glass && !isDark -> AppTheme(
            style = settings.style,
            brightness = settings.brightness,
            isDark = false,
            background = Color(0xFFF1F1F1),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9E9ED),
            bubbleIncoming = Color.White,
            bubbleOutgoing = if (defaultAccent) Color(0xFFD3D5D8) else lerp(Color(0xFFD3D5D8), accent, 0.075f),
            onSurface = Color(0xFF111113),
            onSurfaceVariant = Color(0xFF5D6167),
            onBubbleIncoming = Color(0xFF111113),
            onBubbleOutgoing = Color(0xFF111113),
            outline = Color(0xFFDEDFE2),
            accent = accent,
            onAccent = onAccent,
            selectedSurface = if (defaultAccent) Color(0xFFF1F4FF) else lerp(Color(0xFFE9E9ED), accent, 0.08f),
            imessage = Color(0xFF2143B0),
            rcs = Color(0xFF00694F),
            sms = Color(0xFF4F545C),
            glass = glassTokens,
        )
        !glass && isDark -> AppTheme(
            style = settings.style,
            brightness = settings.brightness,
            isDark = true,
            background = Color(0xFF0B0C10),
            surface = Color(0xFF191A20),
            surfaceVariant = Color(0xFF272930),
            bubbleIncoming = Color(0xFF30333B),
            bubbleOutgoing = lerp(Color(0xFF30333D), accent, 0.20f),
            onSurface = Color(0xFFF8F9FC),
            onSurfaceVariant = Color(0xFFBEC3CD),
            onBubbleIncoming = Color(0xFFF8F9FC),
            onBubbleOutgoing = Color.White,
            outline = Color(0xFF464A53),
            accent = accent,
            onAccent = onAccent,
            selectedSurface = lerp(Color(0xFF30333B), accent, 0.18f),
            imessage = Color(0xFFA8BDFF),
            rcs = Color(0xFF94DEBE),
            sms = Color(0xFFDADEE8),
            glass = glassTokens,
        )
        glass && !isDark -> AppTheme(
            style = settings.style,
            brightness = settings.brightness,
            isDark = false,
            background = Color(0xFFEAF0FA),
            surface = Color.White.copy(alpha = 0.52f),
            surfaceVariant = Color.White.copy(alpha = 0.40f),
            bubbleIncoming = Color.White.copy(alpha = 0.70f),
            bubbleOutgoing = lerp(Color(0xFFD3D5D8), accent, 0.12f).copy(alpha = 0.72f),
            onSurface = Color(0xFF111113),
            onSurfaceVariant = Color(0xFF525A67),
            onBubbleIncoming = Color(0xFF111113),
            onBubbleOutgoing = Color(0xFF111113),
            outline = Color(0xFFD2DAE8),
            accent = accent,
            onAccent = onAccent,
            selectedSurface = lerp(Color.White, accent, 0.12f).copy(alpha = 0.72f),
            imessage = Color(0xFF2143B0),
            rcs = Color(0xFF00694F),
            sms = Color(0xFF4F545C),
            glass = glassTokens,
        )
        else -> AppTheme(
            style = settings.style,
            brightness = settings.brightness,
            isDark = true,
            background = Color(0xFF080B12),
            surface = Color(0xFF202837).copy(alpha = 0.56f),
            surfaceVariant = Color(0xFF2B3444).copy(alpha = 0.57f),
            bubbleIncoming = Color(0xFF303746).copy(alpha = 0.72f),
            bubbleOutgoing = lerp(Color(0xFF294DAF), accent, 0.18f).copy(alpha = 0.70f),
            onSurface = Color(0xFFF9FAFC),
            onSurfaceVariant = Color(0xFFBBC3D2),
            onBubbleIncoming = Color(0xFFF9FAFC),
            onBubbleOutgoing = Color.White,
            outline = Color(0xFF596273),
            accent = accent,
            onAccent = onAccent,
            selectedSurface = lerp(Color(0xFF343D50), accent, 0.20f).copy(alpha = 0.76f),
            imessage = Color(0xFFA8BDFF),
            rcs = Color(0xFF94DEBE),
            sms = Color(0xFFDADEE8),
            glass = glassTokens,
        )
    }
}

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
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val palette = resolveAppTheme(settings, isSystemInDarkTheme())
    val defaultSolidLight = settings.style == SurfaceStyle.SOLID && !palette.isDark &&
        normalizeAccentArgb(settings.accentArgb) == AccentPresets.first().argb
    val colors = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.selectedSurface,
            onPrimaryContainer = palette.onSurface,
            secondary = palette.imessage,
            onSecondary = palette.background,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceVariant,
            surfaceDim = palette.background,
            surfaceBright = palette.surfaceVariant,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceVariant,
            surfaceContainerHighest = palette.surfaceVariant,
            outline = palette.outline,
            outlineVariant = palette.outline.copy(alpha = 0.7f),
            inverseSurface = palette.onSurface,
            inverseOnSurface = palette.background,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = if (defaultSolidLight) Color(0xFFE9EDFF) else palette.selectedSurface,
            onPrimaryContainer = if (defaultSolidLight) Color(0xFF132686) else palette.onSurface,
            secondary = palette.imessage,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceVariant,
            surfaceDim = palette.background,
            surfaceBright = palette.surface,
            surfaceContainerLowest = palette.surface,
            surfaceContainerLow = if (defaultSolidLight) Color(0xFFFAFAFA) else palette.surfaceVariant,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceVariant,
            surfaceContainerHighest = palette.surfaceVariant,
            outline = palette.outline,
            outlineVariant = palette.outline,
            inverseSurface = palette.onSurface,
            inverseOnSurface = palette.surface,
        )
    }
    CompositionLocalProvider(LocalAppTheme provides palette) {
        MaterialTheme(colorScheme = colors, typography = UnifiedTypography, content = content)
    }
}
