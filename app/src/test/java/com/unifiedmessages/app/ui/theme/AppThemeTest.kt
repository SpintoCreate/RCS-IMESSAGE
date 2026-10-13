package com.unifiedmessages.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test
    fun `default keeps the existing solid light palette`() {
        val theme = resolveAppTheme(AppearanceSettings(), systemDark = true)

        assertEquals(SurfaceStyle.SOLID, theme.style)
        assertFalse(theme.isDark)
        assertEquals(Color(0xFF0735FF), theme.accent)
        assertEquals(Color(0xFFF1F1F1), theme.background)
        assertEquals(Color.White, theme.surface)
        assertEquals(Color(0xFFE9E9ED), theme.surfaceVariant)
        assertEquals(Color(0xFFD3D5D8), theme.bubbleOutgoing)
        assertEquals(Color(0xFFF1F4FF), theme.selectedSurface)
        assertEquals(Color(0xFF5582BD), theme.welcomeGlowPrimary)
        assertEquals(Color(0xFF264771), theme.welcomeGlowSecondary)
        assertEquals(0.dp, theme.glass.blurRadius)
    }

    @Test
    fun `all four explicit style and brightness combinations resolve`() {
        for (style in SurfaceStyle.entries) {
            for (brightness in listOf(Brightness.LIGHT, Brightness.DARK)) {
                val theme = resolveAppTheme(
                    AppearanceSettings(style = style, brightness = brightness),
                    systemDark = brightness == Brightness.LIGHT,
                )

                assertEquals(style, theme.style)
                assertEquals(brightness == Brightness.DARK, theme.isDark)
                assertEquals(Color(0xFF0735FF), theme.accent)
                if (style == SurfaceStyle.LIQUID_GLASS) {
                    assertTrue(theme.glass.blurRadius > 0.dp)
                    assertTrue(theme.surface.alpha < 1f)
                    assertTrue(theme.glass.borderGradient.size >= 2)
                } else {
                    assertEquals(0.dp, theme.glass.blurRadius)
                    assertEquals(1f, theme.surface.alpha)
                }
            }
        }
    }

    @Test
    fun `system brightness follows device while explicit brightness overrides it`() {
        val system = AppearanceSettings(brightness = Brightness.SYSTEM)

        assertFalse(resolveAppTheme(system, systemDark = false).isDark)
        assertTrue(resolveAppTheme(system, systemDark = true).isDark)
        assertFalse(resolveAppTheme(system.copy(brightness = Brightness.LIGHT), systemDark = true).isDark)
        assertTrue(resolveAppTheme(system.copy(brightness = Brightness.DARK), systemDark = false).isDark)
    }

    @Test
    fun `accent changes outgoing tint and active surface without changing other choices`() {
        val original = AppearanceSettings(style = SurfaceStyle.SOLID, brightness = Brightness.DARK)
        val changed = original.copy(accentArgb = AccentPresets.last().argb)
        val originalTheme = resolveAppTheme(original, systemDark = false)
        val changedTheme = resolveAppTheme(changed, systemDark = false)

        assertEquals(originalTheme.style, changedTheme.style)
        assertEquals(originalTheme.isDark, changedTheme.isDark)
        assertEquals(Color(AccentPresets.last().argb), changedTheme.accent)
        assertNotEquals(originalTheme.bubbleOutgoing, changedTheme.bubbleOutgoing)
        assertNotEquals(originalTheme.selectedSurface, changedTheme.selectedSurface)
        assertEquals(10, AccentPresets.size)
    }

    @Test
    fun `custom accent becomes opaque and zero falls back to blue`() {
        assertEquals(0xFF123456.toInt(), normalizeAccentArgb(0x00123456))
        assertEquals(AccentPresets.first().argb, normalizeAccentArgb(0))
    }
}
