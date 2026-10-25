package com.coursetable.app.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

internal enum class ListItemMaterial { Page, Inset, Glass }

internal object ListItemDefaults {
    val Shape = RoundedCornerShape(18.dp)
    val GroupShape = RoundedCornerShape(20.dp)
    val Gap = 8.dp
    val IconSize = 32.dp
    val IconGap = 12.dp
    val TextInset = 60.dp
}

internal object ManagementListDefaults {
    val Gap = 12.dp
    val MinHeight = 72.dp
    val ContentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp)
    val IndicatorGap = 12.dp
    val TextGap = 4.dp
    val TrailingPadding = 4.dp
}

/** Decoration only: focus, gestures and selection semantics belong to the caller. */
@Composable
internal fun Modifier.listItemSurface(
    material: ListItemMaterial? = null,
    selected: Boolean = false,
    shape: Shape = ListItemDefaults.Shape,
    opacity: Float = 1f,
    accent: Color = LiquidTheme.colorScheme.primary
): Modifier {
    val colors = LiquidTheme.colorScheme
    val dark = colors.isDark
    val resolved = material ?: if (LocalBackInOverlay.current) ListItemMaterial.Glass else ListItemMaterial.Page
    val strength = opacity.coerceIn(0f, 1f)
    val overlayBackdrop = LocalOverlayGlassBackdrop.current
    val backdrop = overlayBackdrop ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val preferReadability = LocalReadableOverlayGlass.current
    var result = this.clip(shape)
    if (resolved == ListItemMaterial.Glass && backdrop != null && capability != GlassCapability.STATIC) {
        result = result.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(brightness = if (dark || preferReadability) 0f else 0.035f,
                    saturation = if (preferReadability) 1f else 1.12f)
                if (overlayBackdrop == null) blur((if (preferReadability) 40.dp else 32.dp).toPx())
                if (!preferReadability && capability == GlassCapability.FULL) {
                    lens(refractionHeight = 6.dp.toPx(), refractionAmount = 8.dp.toPx(), depthEffect = true)
                }
            },
            highlight = { Highlight.Default.copy(alpha = if (dark) 0.28f else 0.50f) },
            shadow = { Shadow(radius = 0.dp, color = Color.Transparent) },
            innerShadow = { InnerShadow(radius = 2.dp, alpha = if (dark) 0.14f else 0.08f) },
            onDrawSurface = {
                drawRect((if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = 0.20f * strength))
            }
        )
    } else {
        val alpha = when (resolved) {
            ListItemMaterial.Page -> if (dark) 0.86f else 0.90f
            ListItemMaterial.Inset -> if (dark) 0.15f else 0.23f
            ListItemMaterial.Glass -> if (dark) 0.72f else 0.78f
        }
        result = result.background(colors.surface.copy(alpha = alpha * strength))
    }
    val wash = if (selected) {
        listOf(accent.copy(alpha = (if (dark) 0.16f else 0.12f) * strength), accent.copy(alpha = 0.045f * strength))
    } else {
        val alpha = if (resolved == ListItemMaterial.Glass) { if (dark) 0.055f else 0.16f } else { if (dark) 0.025f else 0.07f }
        listOf(Color.White.copy(alpha = alpha * strength), Color.White.copy(alpha = 0.015f * strength))
    }
    // Glass rows use one wash throughout; reserve vertical shading for page materials.
    result = if (resolved == ListItemMaterial.Glass && !selected) {
        result.background(wash.first())
    } else {
        result.background(Brush.verticalGradient(wash))
    }
    return result
}

@Composable
internal fun ListItemSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    material: ListItemMaterial? = null,
    shape: Shape = ListItemDefaults.Shape,
    opacity: Float = 1f,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides LiquidTheme.colorScheme.onSurface) {
        Box(modifier.listItemSurface(material, selected, shape, opacity)) { content() }
    }
}

@Composable
internal fun ListItemIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LiquidTheme.colorScheme.primary,
    size: Dp = ListItemDefaults.IconSize
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = if (LiquidTheme.colorScheme.isDark) 0.13f else 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
    }
}

@Composable
internal fun ListItemChevron(modifier: Modifier = Modifier) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier.size(18.dp),
        tint = LiquidTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ListChoiceIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        null, modifier.size(20.dp), tint = if (selected) LiquidTheme.colorScheme.primary
        else LiquidTheme.colorScheme.onSurfaceVariant)
}
