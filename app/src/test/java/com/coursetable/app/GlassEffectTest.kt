package com.coursetable.app

import com.coursetable.app.data.GlassEffect
import com.coursetable.app.ui.liquid.chromeGlassTint
import org.junit.Assert.*
import org.junit.Test

class GlassEffectTest {
    @Test fun upgradesAndUnknownKeysUseStandardGlass() {
        assertEquals(GlassEffect.STANDARD, GlassEffect.fromKey(null))
        assertEquals(GlassEffect.STANDARD, GlassEffect.fromKey("future-preset"))
        for (effect in GlassEffect.entries) assertEquals(effect, GlassEffect.fromKey(effect.key))
    }

    @Test fun clearRemovesOnlyTheLiveTintInBothThemes() {
        for (dark in listOf(false, true)) {
            val standard = chromeGlassTint(GlassEffect.STANDARD, dark, glassEnabled = true)
            val clear = chromeGlassTint(GlassEffect.CLEAR, dark, glassEnabled = true)
            assertTrue("Standard glass keeps a light surface tint", standard.alpha in .1f.. .35f)
            assertEquals("Clear glass must show the sampled scene without a tint", 0f, clear.alpha, 0f)
            for (effect in GlassEffect.entries) {
                val fallback = chromeGlassTint(effect, dark, glassEnabled = false)
                assertTrue("Controls remain visible without backdrop support", fallback.alpha >= standard.alpha)
            }
        }
    }
}
