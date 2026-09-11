package com.coursetable.app.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs

/** CourseTable adapter for AndroidLiquidGlass' native LiquidToggle. */
@Composable
internal fun NativeLiquidToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val backdrop = LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val accentColor = if (colors.isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackColor = if (colors.isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.20f)
    }
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidthPx = with(density) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val animation = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f
        )
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            animation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val glassEnabled = backdrop != null && capability != GlassCapability.STATIC
    val inputModifier = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier.pointerInput(animation, checked, isLtr) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                down.consume()
                animation.press()
                var totalDrag = 0f
                var finished = false
                var cancelled = false
                while (!finished) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        cancelled = true
                        finished = true
                    } else if (!change.pressed) {
                        change.consume()
                        finished = true
                    } else {
                        val dx = change.position.x - change.previousPosition.x
                        change.consume()
                        if (dx != 0f) {
                            totalDrag += dx
                            val logicalDx = if (isLtr) dx else -dx
                            fraction = (fraction + logicalDx / dragWidthPx).coerceIn(0f, 1f)
                            animation.updateValue(fraction)
                        }
                    }
                }

                val target = when {
                    cancelled -> if (checked) 1f else 0f
                    abs(totalDrag) < viewConfiguration.touchSlop -> if (checked) 0f else 1f
                    animation.targetValue >= 0.5f -> 1f
                    else -> 0f
                }
                fraction = target
                animation.animateToValue(target)
                if (!cancelled) onCheckedChange(target == 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "已开启" else "已关闭"
                if (onCheckedChange != null) {
                    onClick {
                        onCheckedChange(!checked)
                        true
                    }
                }
            }
            .then(inputModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerpColor(trackColor, accentColor, animation.value))
                }
                .size(width = 64.dp, height = 28.dp)
        )

        val thumbPosition = Modifier.graphicsLayer {
            val padding = 2.dp.toPx()
            translationX = if (isLtr) {
                lerp(padding, padding + dragWidthPx, animation.value)
            } else {
                lerp(-padding, -(padding + dragWidthPx), animation.value)
            }
        }

        if (glassEnabled) {
            Box(
                thumbPosition
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawRecordedTrack ->
                                val progress = animation.pressProgress
                                scale(
                                    scaleX = lerp(2f / 3f, 0.75f, progress),
                                    scaleY = lerp(0f, 0.75f, progress)
                                ) {
                                    drawRecordedTrack()
                                }
                            }
                        ),
                        shape = { Capsule() },
                        effects = {
                            val progress = animation.pressProgress
                            blur(8.dp.toPx() * (1f - progress))
                            lens(
                                5.dp.toPx() * progress,
                                10.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = animation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                        },
                        innerShadow = {
                            val progress = animation.pressProgress
                            InnerShadow(radius = 4.dp * progress, alpha = progress)
                        },
                        layerBlock = {
                            scaleX = animation.scaleX
                            scaleY = animation.scaleY
                            val velocity = animation.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                        }
                    )
                    .size(width = 40.dp, height = 24.dp)
            )
        } else {
            Box(
                thumbPosition
                    .graphicsLayer {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                    }
                    .size(width = 40.dp, height = 24.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, Capsule())
            )
        }
    }
}
