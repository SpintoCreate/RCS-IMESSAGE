package com.unifiedmessages.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceContrastTest {
    @Test
    fun `secondary text badges overlays and fab stay readable in blue and lime appearances`() {
        for (style in SurfaceStyle.entries) {
            for (brightness in listOf(Brightness.LIGHT, Brightness.DARK)) {
                for (accent in listOf(AccentPresets.first(), AccentPresets.first { it.name == "Lime" })) {
                    val theme = resolveAppTheme(
                        AppearanceSettings(style = style, brightness = brightness, accentArgb = accent.argb),
                        systemDark = false,
                    )
                    val context = "${style.name}/${brightness.name}/${accent.name}"
                    val surface = theme.surface.compositeOver(theme.background)
                    for (background in listOf(theme.background, surface)) {
                        assertReadable(context, theme.onSurfaceVariant, background, 4.5f)
                    }

                    val badgeBase = if (style == SurfaceStyle.LIQUID_GLASS) {
                        theme.surface.copy(alpha = 0.88f).compositeOver(surface)
                    } else {
                        surface
                    }
                    for (sourceColor in listOf(theme.imessage, theme.rcs, theme.sms)) {
                        val badgeFill = sourceColor.copy(alpha = 0.1f).compositeOver(badgeBase)
                        assertReadable("$context source badge", sourceColor, badgeFill, 4.5f)
                    }

                    val overlay = surface.copy(alpha = 0.96f).compositeOver(theme.background)
                    assertReadable("$context dialog title", theme.onSurface, overlay, 4.5f)
                    assertReadable("$context dialog body", theme.onSurfaceVariant, overlay, 4.5f)
                    assertReadable("$context confirm", theme.onAccent, theme.accent, 4.5f)

                    val fabFill = if (style == SurfaceStyle.LIQUID_GLASS) {
                        theme.accent.copy(alpha = 0.86f).compositeOver(theme.background)
                    } else {
                        theme.accent
                    }
                    val fabMinimum = if (style == SurfaceStyle.LIQUID_GLASS && !theme.isDark) 4.5f else 3f
                    assertReadable("$context FAB", theme.onAccent, fabFill, fabMinimum)
                }
            }
        }
    }

    private fun assertReadable(context: String, foreground: Color, background: Color, minimum: Float) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue("$context contrast $ratio is below $minimum", ratio >= minimum)
    }
}
