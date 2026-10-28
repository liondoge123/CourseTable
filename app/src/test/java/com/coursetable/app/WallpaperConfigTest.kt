package com.coursetable.app

import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.wallpaperPlacement
import org.junit.Assert.*
import org.junit.Test

class WallpaperConfigTest {
    private val id = "12345678-1234-1234-1234-123456789abc.png"

    @Test fun configurationRoundTripsWithoutChangingExistingThemeSettings() {
        val config = WallpaperConfig(id, .2f, .7f, 2f, 12f, .45f, .25f, .4f)
        assertEquals(config, WallpaperConfig.decode(config.encode()))
        assertNull(WallpaperConfig.decode(null))
        assertNull(WallpaperConfig.decode("not json"))
        assertNull(WallpaperConfig.decode("{\"imageId\":\"../../secret.png\"}"))
    }

    @Test fun defaultsAndInvalidAdjustmentsAreBounded() {
        val config = WallpaperConfig.decode("{\"imageId\":\"$id\"}")!!
        assertEquals(WallpaperConfig(id), config)
        val bounded = WallpaperConfig(id, Float.NaN, -2f, Float.POSITIVE_INFINITY, 99f, 2f).normalized()
        assertEquals(.5f, bounded.positionX)
        assertEquals(0f, bounded.positionY)
        assertEquals(1f, bounded.scale)
        assertEquals(24f, bounded.blurDp)
        assertEquals(.8f, bounded.mask)
        assertFalse(WallpaperConfig.validId("../$id"))
    }

    @Test fun gridOpacitySupportsLegacySettingsEndpointsAndInvalidValues() {
        val legacy = WallpaperConfig.decode("{\"imageId\":\"$id\",\"mask\":0.5}")!!
        assertEquals(.65f, legacy.gridOpacity, 0f)
        for (opacity in listOf(0f, .25f, 1f)) {
            val config = legacy.copy(gridOpacity = opacity)
            assertEquals(config, WallpaperConfig.decode(config.encode()))
        }
        assertEquals(0f, legacy.copy(gridOpacity = -1f).normalized().gridOpacity, 0f)
        assertEquals(1f, legacy.copy(gridOpacity = 2f).normalized().gridOpacity, 0f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(.65f, legacy.copy(gridOpacity = invalid).normalized().gridOpacity, 0f)
        }
    }

    @Test fun wideAndTallImagesFillViewportWithoutExposingEdges() {
        val wide = wallpaperPlacement(2000, 1000, 400f, 800f, WallpaperConfig(id))
        assertEquals(1600f, wide.size.width, .01f)
        assertEquals(800f, wide.size.height, .01f)
        assertEquals(-600f, wide.origin.x, .01f)
        val tall = wallpaperPlacement(1000, 3000, 400f, 800f, WallpaperConfig(id, positionY = 1f))
        assertEquals(400f, tall.size.width, .01f)
        assertEquals(-400f, tall.origin.y, .01f)
    }

    @Test fun courseCardOpacityIsIndependentAndLegacyCardsStayOpaque() {
        val legacy = WallpaperConfig.decode("{\"imageId\":\"$id\",\"gridOpacity\":0.25}")!!
        assertEquals(1f, legacy.cardOpacity, 0f)
        for (opacity in listOf(0f, .4f, 1f)) {
            val config = legacy.copy(cardOpacity = opacity)
            val saved = WallpaperConfig.decode(config.encode())!!
            assertEquals(opacity, saved.cardOpacity, 0f)
            assertEquals(.25f, saved.gridOpacity, 0f)
        }
        assertEquals(0f, legacy.copy(cardOpacity = -1f).normalized().cardOpacity, 0f)
        assertEquals(1f, legacy.copy(cardOpacity = 2f).normalized().cardOpacity, 0f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, legacy.copy(cardOpacity = invalid).normalized().cardOpacity, 0f)
        }
    }

    @Test fun normalizedCropSurvivesViewportChangesAndZoomClamps() {
        val config = WallpaperConfig(id, 1f, 0f, 9f)
        for ((width, height) in listOf(400f to 800f, 800f to 400f)) {
            val placed = wallpaperPlacement(1200, 800, width, height, config)
            assertEquals(width, placed.origin.x + placed.size.width, .01f)
            assertEquals(0f, placed.origin.y, .01f)
            assertTrue(placed.size.width >= width && placed.size.height >= height)
        }
    }

    @Test fun legacyWallpapersEnableAutomaticTextProtectionAndChoicesRoundTrip() {
        val legacy = WallpaperConfig.decode("{\"imageId\":\"$id\"}")!!
        assertEquals(WallpaperTextMode.AUTO, legacy.textMode)
        assertTrue(legacy.textClarity)
        for (mode in WallpaperTextMode.entries) for (clarity in listOf(false, true)) {
            val config = legacy.copy(textMode = mode, textClarity = clarity, gridOpacity = .1f, cardOpacity = .4f)
            assertEquals(config, WallpaperConfig.decode(config.encode()))
        }
        assertEquals(WallpaperTextMode.AUTO,
            WallpaperConfig.decode("{\"imageId\":\"$id\",\"textMode\":\"unknown\"}")!!.textMode)
    }
}
