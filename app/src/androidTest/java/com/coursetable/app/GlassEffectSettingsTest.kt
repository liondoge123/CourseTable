package com.coursetable.app

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coursetable.app.data.GlassEffect
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.data.Timetable
import com.coursetable.app.data.WallpaperConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GlassEffectSettingsTest {
    @Test fun presetSurvivesRestartTableSwitchAndOtherSettingsWrites() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            assertEquals(GlassEffect.STANDARD.key, env.settings.settings.first().glassEffect)
            val wallpaper = WallpaperConfig("12345678-1234-1234-1234-123456789abc.png", gridOpacity = .25f)
            env.settings.saveWallpaper(wallpaper)
            env.settings.save(glassEffect = GlassEffect.CLEAR.key)
            env.settings.save(themeMode = "dark", themeColor = "green")
            env.database.timetableDao().upsert(Timetable(id = 101, name = "秋季"))
            env.database.timetableDao().upsert(Timetable(id = 102, name = "春季"))
            env.settings.setActiveTimetable(101)
            env.settings.setActiveTimetable(102)
            val restarted = SettingsRepository(env.context, env.database.timetableDao(), env.preferences)
            val saved = restarted.settings.first()
            assertEquals(GlassEffect.CLEAR.key, saved.glassEffect)
            assertEquals(wallpaper, saved.wallpaper)
            assertEquals("dark", saved.themeMode)
            assertEquals("green", saved.themeColor)
            restarted.saveAll(saved.copy(glassEffect = GlassEffect.STANDARD.key))
            assertEquals(GlassEffect.STANDARD.key, restarted.settings.first().glassEffect)
            assertEquals(wallpaper, restarted.settings.first().wallpaper)
        }
    }

    @Test fun unknownStoredPresetFallsBackToStandard() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            env.preferences.edit { it[stringPreferencesKey("glass_effect")] = "future-preset" }
            assertEquals(GlassEffect.STANDARD.key, env.settings.settings.first().glassEffect)
            env.settings.save(glassEffect = "invalid")
            assertEquals(GlassEffect.STANDARD.key, env.settings.settings.first().glassEffect)
        }
    }
}
