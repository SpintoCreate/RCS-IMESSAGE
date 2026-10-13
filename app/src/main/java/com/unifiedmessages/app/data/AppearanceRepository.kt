package com.unifiedmessages.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unifiedmessages.app.ui.theme.AccentPresets
import com.unifiedmessages.app.ui.theme.AppearanceSettings
import com.unifiedmessages.app.ui.theme.Brightness
import com.unifiedmessages.app.ui.theme.SurfaceStyle
import com.unifiedmessages.app.ui.theme.normalizeAccentArgb
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/**
 * Owns the three independent appearance preferences. The old two-look preference is read once as
 * the fallback for installations upgraded from version 0.2, then written to DataStore on first edit.
 */
class AppearanceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(
        "unified_messages_display",
        Context.MODE_PRIVATE,
    )

    private val legacySettings: AppearanceSettings = when (
        legacyPreferences.getString("visual_style_v2", null)
    ) {
        "LIQUID_GLASS" -> AppearanceSettings(
            style = SurfaceStyle.LIQUID_GLASS,
            brightness = Brightness.DARK,
        )
        else -> AppearanceSettings()
    }

    val settings: Flow<AppearanceSettings> = appContext.appearanceDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { stored ->
            val empty = !stored.contains(STYLE) && !stored.contains(BRIGHTNESS) &&
                !stored.contains(ACCENT_ARGB)
            val fallback = if (empty) legacySettings else AppearanceSettings()
            AppearanceSettings(
                style = stored[STYLE]?.let { name ->
                    SurfaceStyle.entries.firstOrNull { it.name == name }
                } ?: fallback.style,
                brightness = stored[BRIGHTNESS]?.let { name ->
                    Brightness.entries.firstOrNull { it.name == name }
                } ?: fallback.brightness,
                accentArgb = stored[ACCENT_ARGB]?.let(::normalizeAccentArgb)
                    ?: fallback.accentArgb,
            )
        }

    suspend fun update(settings: AppearanceSettings) {
        appContext.appearanceDataStore.edit { stored ->
            stored[STYLE] = settings.style.name
            stored[BRIGHTNESS] = settings.brightness.name
            stored[ACCENT_ARGB] = normalizeAccentArgb(settings.accentArgb)
        }
    }

    suspend fun updateStyle(style: SurfaceStyle) {
        appContext.appearanceDataStore.edit { stored ->
            stored.ensureLegacyMigration()
            stored[STYLE] = style.name
        }
    }

    suspend fun updateBrightness(brightness: Brightness) {
        appContext.appearanceDataStore.edit { stored ->
            stored.ensureLegacyMigration()
            stored[BRIGHTNESS] = brightness.name
        }
    }

    suspend fun updateAccent(argb: Int) {
        appContext.appearanceDataStore.edit { stored ->
            stored.ensureLegacyMigration()
            stored[ACCENT_ARGB] = normalizeAccentArgb(argb)
        }
    }

    private fun MutablePreferences.ensureLegacyMigration() {
        if (!contains(STYLE) && !contains(BRIGHTNESS) && !contains(ACCENT_ARGB)) {
            this[STYLE] = legacySettings.style.name
            this[BRIGHTNESS] = legacySettings.brightness.name
            this[ACCENT_ARGB] = AccentPresets.first().argb
        }
    }

    private companion object {
        val STYLE = stringPreferencesKey("surface_style")
        val BRIGHTNESS = stringPreferencesKey("brightness")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
    }
}
