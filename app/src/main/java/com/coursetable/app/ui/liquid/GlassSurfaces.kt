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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
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

internal enum class OverlayGlassStyle { SHEET, DIALOG, MENU }

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
    style: OverlayGlassStyle = OverlayGlassStyle.DIALOG,
    content: @Composable () -> Unit
) {
    val backdrop = LocalOverlayGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val isLightTheme = !colors.isDark
    val requestedTint = baseColor ?: if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF121212)
    val liveAlpha = when (style) {
        OverlayGlassStyle.SHEET -> if (isLightTheme) 0.50f else 0.44f
        OverlayGlassStyle.DIALOG -> if (isLightTheme) 0.26f else 0.23f
        OverlayGlassStyle.MENU -> if (isLightTheme) 0.30f else 0.26f
    }
    val blurRadius = when (style) {
        OverlayGlassStyle.SHEET -> 32.dp
        OverlayGlassStyle.DIALOG -> 22.dp
        OverlayGlassStyle.MENU -> 18.dp
    }
    val refractionHeight = when (style) {
        OverlayGlassStyle.SHEET -> 8.dp
        OverlayGlassStyle.DIALOG -> 18.dp
        OverlayGlassStyle.MENU -> 12.dp
    }
    val refractionAmount = when (style) {
        OverlayGlassStyle.SHEET -> 12.dp
        OverlayGlassStyle.DIALOG -> 30.dp
        OverlayGlassStyle.MENU -> 18.dp
    }
    val liveTint = requestedTint.copy(alpha = liveAlpha)
    val liveSheen = Color.White.copy(alpha = if (isLightTheme) 0.14f else 0.055f)
    val lowerTint = liveTint.copy(alpha = liveAlpha * 0.88f)
    val fallbackTint = requestedTint.copy(alpha = if (isLightTheme) 0.86f else 0.88f)
    val fallbackSheen = Color.White.copy(alpha = if (isLightTheme) 0.34f else 0.09f)

    // A single readable liquid-glass material for dialogs, sheets and menus.
    val surfaceModifier = if (backdrop != null && capability != GlassCapability.STATIC) {
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(
                        brightness = if (isLightTheme) 0.035f else 0f,
                        saturation = if (isLightTheme) 1.18f else 1.12f
                    )
                    blur(blurRadius.toPx())
                    if (capability == GlassCapability.FULL && style != OverlayGlassStyle.SHEET) {
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = true
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (isLightTheme) 0.58f else 0.34f)
                },
                shadow = {
                    Shadow(
                        radius = shadowElevation,
                        color = if (shadowElevation == 0.dp) Color.Transparent else Color.Black.copy(alpha = if (isLightTheme) 0.14f else 0.34f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 3.dp,
                        alpha = if (isLightTheme) 0.12f else 0.22f
                    )
                },
                onDrawSurface = {
                    if (style == OverlayGlassStyle.SHEET) {
                        // Keep the material equally frosted through the bottom safe area and
                        // around the rim; the sheen adds light without thinning the tint.
                        drawRect(liveTint)
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(liveSheen, Color.Transparent, Color.Transparent)
                            )
                        )
                    } else {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(liveSheen, liveTint, liveTint, lowerTint)
                            )
                        )
                    }
                }
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    } else {
        modifier
            .shadow(shadowElevation, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(fallbackSheen, fallbackTint, fallbackTint)
                )
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(surfaceModifier.testTag("overlay-glass-${style.name.lowercase()}")) {
            if (style == OverlayGlassStyle.SHEET && backdrop != null && capability != GlassCapability.STATIC) {
                val edgeColor = requestedTint.copy(alpha = if (isLightTheme) 0.88f else 0.78f)
                val clearEdge = requestedTint.copy(alpha = 0f)
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .drawWithCache {
                            val solidEdgeWidth = 24.dp.toPx()
                            val fadeEdgeWidth = 64.dp.toPx()
                            val xSolid = (solidEdgeWidth / size.width).coerceIn(0f, 0.5f)
                            val xClear = (fadeEdgeWidth / size.width).coerceIn(xSolid, 0.5f)
                            val ySolid = (solidEdgeWidth / size.height).coerceIn(0f, 0.5f)
                            val yClear = (fadeEdgeWidth / size.height).coerceIn(ySolid, 0.5f)
                            val horizontalVeil = Brush.horizontalGradient(
                                0f to edgeColor,
                                xSolid to edgeColor,
                                xClear to clearEdge,
                                (1f - xClear) to clearEdge,
                                (1f - xSolid) to edgeColor,
                                1f to edgeColor
                            )
                            val verticalVeil = Brush.verticalGradient(
                                0f to edgeColor,
                                ySolid to edgeColor,
                                yClear to clearEdge,
                                (1f - yClear) to clearEdge,
                                (1f - ySolid) to edgeColor,
                                1f to edgeColor
                            )
                            onDrawBehind {
                                drawRect(horizontalVeil)
                                drawRect(verticalVeil)
                            }
                        }
                )
            }
            content()
        }
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
