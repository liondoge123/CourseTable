package com.coursetable.app.ui.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

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
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    baseColor: Color? = null,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    shadowElevation: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier.then(
            if (baseColor != null) Modifier.background(baseColor.copy(alpha = 0.14f), shape) else Modifier
        ),
        shape = shape,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        style = GlassStyle.OVERLAY,
        content = content
    )
}

/**
 * Liquid glass floating capsule for top navigation and compact action bars,
 * matching the authentic liquid glass materials of the bottom navigation dock.
 * Features:
 * - Authentic Backdrop lens refraction (lens + vibrancy + blur + chromatic aberration)
 * - Smooth physics-based spring press scaling (matching bottom dock items)
 * - Deepening lens curvature and specular highlight on press
 * - Resilient fallback for static environments
 */
@Composable
fun LiquidCapsuleSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    baseColor: Color? = null,
    borderColor: Color? = null,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    shadowElevation: Dp = 3.dp,
    externalPressProgress: Float? = null,
    content: @Composable () -> Unit
) {
    val backdrop = LocalTopChromeGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val internalPressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
        label = "capsulePress"
    )
    val pressProgress = externalPressProgress ?: internalPressProgress
    val scale = lerp(1f, 1.05f, pressProgress)
    val borderStroke = BorderStroke(0.8.dp, borderColor ?: colors.glassBorder)
    val glassEnabled = backdrop != null && capability != GlassCapability.STATIC
    val containerColor = if (colors.isDark) {
        Color(0xFF121212).copy(alpha = 0.40f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.40f)
    }

    val baseModifier = modifier.then(
        if (baseColor != null) Modifier.background(baseColor, shape) else Modifier
    )

    val surfaceModifier = if (glassEnabled) {
        baseModifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 24.dp.toPx()
                    )
                },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(containerColor)
                }
            )
            .border(borderStroke, shape)
    } else {
        baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor, shape)
            .border(borderStroke, shape)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = surfaceModifier.then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick
                        )
                } else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
