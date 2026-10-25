package com.coursetable.app.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.coursetable.app.data.GlassEffect
import com.coursetable.app.ui.theme.LiquidTheme

/** Shared tint for top capsules, navigation tabs, the bridge and its detached action. */
internal fun chromeGlassTint(effect: GlassEffect, isDark: Boolean, glassEnabled: Boolean): Color {
    // Systems without backdrop effects retain a visible control surface in either preset.
    val alpha = when {
        !glassEnabled -> .40f
        effect == GlassEffect.CLEAR -> 0f
        else -> .24f
    }
    return (if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = alpha)
}

@Composable
internal fun chromeGlassTint(glassEnabled: Boolean): Color =
    chromeGlassTint(LiquidTheme.glassEffect, LiquidTheme.colorScheme.isDark, glassEnabled)
