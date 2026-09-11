package com.coursetable.app.ui.liquid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    shadowElevation: Dp = 10.dp,
    style: GlassStyle = GlassStyle.CHROME,
    content: @Composable () -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val surfaceAlpha = when (style) {
        GlassStyle.CHROME -> if (colors.isDark) 0.46f else 0.38f
        GlassStyle.CONTROL -> if (colors.isDark) 0.58f else 0.48f
        GlassStyle.OVERLAY -> if (colors.isDark) 0.72f else 0.64f
    }
    val styled = if (backdrop != null && capability != GlassCapability.STATIC) {
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur((if (style == GlassStyle.OVERLAY) 18.dp else 12.dp).toPx())
                    if (capability == GlassCapability.FULL) {
                        lens(
                            refractionHeight = (if (style == GlassStyle.CONTROL) 8.dp else 12.dp).toPx(),
                            refractionAmount = (if (style == GlassStyle.CONTROL) 12.dp else 18.dp).toPx(),
                            chromaticAberration = style == GlassStyle.CONTROL
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (colors.isDark) 0.44f else 0.72f)
                },
                shadow = {
                    Shadow(radius = shadowElevation, color = Color.Black.copy(alpha = if (colors.isDark) 0.30f else 0.12f))
                },
                innerShadow = {
                    InnerShadow(radius = 3.dp, alpha = if (colors.isDark) 0.24f else 0.12f)
                },
                onDrawSurface = {
                    drawRect(colors.glass.copy(alpha = surfaceAlpha))
                }
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    } else {
        val base = colors.glass.copy(alpha = if (colors.isDark) 0.94f else 0.90f)
        modifier
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.52f), base, base)
                )
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(styled) { content() }
    }
}

@Composable
internal fun OverlayGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(32.dp),
    baseColor: Color? = null,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    shadowElevation: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val isLightTheme = !LiquidTheme.colorScheme.isDark
    val libraryContainerColor = baseColor ?: if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.60f)
    } else {
        Color(0xFF121212).copy(alpha = 0.40f)
    }

    // Keep this material in sync with AndroidLiquidGlass' DialogContent sample.
    val surfaceModifier = if (backdrop != null && capability != GlassCapability.STATIC) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = if (isLightTheme) 0.20f else 0f,
                    saturation = 1.5f
                )
                blur(if (isLightTheme) 16.dp.toPx() else 8.dp.toPx())
                if (capability == GlassCapability.FULL) {
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 48.dp.toPx(),
                        depthEffect = true
                    )
                }
            },
            highlight = { Highlight.Plain },
            onDrawSurface = { drawRect(libraryContainerColor) }
        )
    } else {
        modifier
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(libraryContainerColor)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(surfaceModifier) { content() }
    }
}

/**
 * Android-native top control based on AndroidLiquidGlass' LiquidButton.
 * CourseTable keeps its own dimensions and optional selected-state tint.
 */
@Composable
fun LiquidCapsuleSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = Capsule(),
    baseColor: Color? = null,
    borderColor: Color? = null,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    shadowElevation: Dp = 3.dp,
    externalPressProgress: Float? = null,
    preferTopChromeBackdrop: Boolean = false,
    content: @Composable () -> Unit
) {
    val ambientBackdrop = LocalGlassBackdrop.current
    val backdrop = if (preferTopChromeBackdrop) {
        LocalTopChromeGlassBackdrop.current ?: ambientBackdrop
    } else {
        ambientBackdrop
    }
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveGlassHighlight(animationScope)
    }
    val pressProgress = externalPressProgress ?: interactiveHighlight.pressProgress
    val glassEnabled = backdrop != null && capability != GlassCapability.STATIC
    val containerColor = if (colors.isDark) {
        Color(0xFF121212).copy(alpha = 0.40f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.40f)
    }

    val surfaceModifier = if (glassEnabled) {
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 24.dp.toPx()
                    )
                },
                layerBlock = {
                    val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, pressProgress)
                    if (onClick != null && enabled && externalPressProgress == null) {
                        val width = size.width
                        val height = size.height
                        val maxOffset = size.minDimension
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        val maxDragScale = 4.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).coerceAtMost(1f)
                        scaleY = scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).coerceAtMost(1f)
                    } else {
                        scaleX = scale
                        scaleY = scale
                    }
                },
                onDrawSurface = {
                    if (baseColor != null) drawRect(baseColor)
                }
            )
            .then(if (onClick != null && enabled) interactiveHighlight.modifier else Modifier)
            .then(
                if (borderColor != null) {
                    Modifier.border(BorderStroke(0.8.dp, borderColor), shape)
                } else Modifier
            )
    } else {
        modifier
            .graphicsLayer {
                val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, pressProgress)
                scaleX = scale
                scaleY = scale
            }
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(baseColor ?: containerColor, shape)
            .then(
                if (borderColor != null) {
                    Modifier.border(BorderStroke(0.8.dp, borderColor), shape)
                } else Modifier
            )
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = surfaceModifier.then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick
                        )
                        .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                } else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
