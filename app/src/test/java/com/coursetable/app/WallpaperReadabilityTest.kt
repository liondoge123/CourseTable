package com.coursetable.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.textContrast
import com.coursetable.app.ui.wallpaperAxisStyle
import com.coursetable.app.ui.wallpaperGridBackground
import com.coursetable.app.ui.wallpaperTextColorOn
import com.coursetable.app.ui.theme.ThemeColor
import org.junit.Assert.*
import org.junit.Test

class WallpaperReadabilityTest {
    private val config = WallpaperConfig("12345678-1234-1234-1234-123456789abc.png")
    private val pixels = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue,
        Color.Yellow, Color.Cyan, Color.Magenta, Color.Gray, Color.Transparent)

    @Test fun protectedAxesMeetSmallTextContrastAcrossWallpaperExtremesAndAllSettings() {
        for (accent in ThemeColor.entries) for (scheme in listOf(accent.lightScheme, accent.darkScheme)) {
            for (mode in WallpaperTextMode.entries) for (mask in listOf(0f, .3f, .8f)) {
                for (opacity in listOf(0f, .25f, .65f, 1f)) for (average in listOf(Color.Black, Color.Gray, Color.White)) {
                    val c = config.copy(textMode = mode, mask = mask, gridOpacity = opacity)
                    val style = wallpaperAxisStyle(c, average, scheme.background, scheme.surface)
                    for (pixel in pixels) {
                        val background = style.scrim.compositeOver(wallpaperGridBackground(pixel, c, scheme.background, scheme.surface))
                        assertTrue("${accent.key}, dark=${scheme.isDark}, $mode, mask=$mask, opacity=$opacity, pixel=$pixel",
                            textContrast(style.foreground, background) >= 4.5f)
                    }
                }
            }
        }
    }

    @Test fun automaticPolarityUsesTheCompositedBackgroundAndManualChoicesRemainStable() {
        val scheme = ThemeColor.BLUE.lightScheme
        val clear = config.copy(mask = 0f, gridOpacity = 0f, textClarity = false)
        val dark = wallpaperAxisStyle(clear, Color.White, scheme.background, scheme.surface)
        val light = wallpaperAxisStyle(clear, Color.Black, scheme.background, scheme.surface)
        assertNotEquals(dark.foreground, light.foreground)
        assertEquals(0f, dark.scrim.alpha, 0f)
        assertEquals(0f, light.scrim.alpha, 0f)
        assertEquals(dark.foreground,
            wallpaperAxisStyle(clear.copy(gridOpacity = 1f), Color.Black, scheme.background, scheme.surface).foreground)
        for (pixel in pixels) {
            assertEquals(dark.foreground, wallpaperAxisStyle(clear.copy(textMode = WallpaperTextMode.DARK),
                pixel, scheme.background, scheme.surface).foreground)
            assertEquals(light.foreground, wallpaperAxisStyle(clear.copy(textMode = WallpaperTextMode.LIGHT),
                pixel, scheme.background, scheme.surface).foreground)
        }
    }

    @Test fun alreadyReadableSurfacesNeedNoExtraScrimAndTodayBadgesStayReadable() {
        for (accent in ThemeColor.entries) for (scheme in listOf(accent.lightScheme, accent.darkScheme)) {
            val style = wallpaperAxisStyle(config.copy(gridOpacity = 1f), Color.Gray, scheme.background, scheme.surface)
            assertEquals(0f, style.scrim.alpha, 0f)
            assertTrue(textContrast(wallpaperTextColorOn(scheme.primary), scheme.primary) >= 4.5f)
        }
    }
}
