package com.coursetable.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A draggable liquid-glass tab bar based on AndroidLiquidGlass' LiquidBottomTabs
 * sample. The invisible second row is recorded separately so the moving lens can
 * reveal the accent-coloured tab content while refracting the app backdrop.
 */
@Composable
fun LiquidNavigationTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tabCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(contentColor: Color, itemScale: Float, selectTab: (Int) -> Unit) -> Unit
) {
    require(tabCount > 0)
    val backdrop = LocalNavigationGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val shape = Capsule()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetAnimation = remember { Animatable(0f) }
    val dragAnimation = remember(scope, tabCount) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f
        )
    }
    var dragging by remember { mutableStateOf(false) }
    // Match AndroidLiquidGlass' LiquidBottomTabs material. Its translucency comes
    // primarily from sampling the real page backdrop, not from a nearly clear tint.
    val dockContainerColor = if (colors.isDark) {
        Color(0xFF121212).copy(alpha = 0.40f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.40f)
    }

    LaunchedEffect(selectedIndex, dragging, dragAnimation) {
        if (!dragging && dragAnimation.targetValue != selectedIndex.toFloat()) {
            dragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    val selectTab: (Int) -> Unit = { requestedIndex ->
        val index = requestedIndex.coerceIn(0, tabCount - 1)
        dragAnimation.animateToValue(index.toFloat())
        onSelected(index)
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val horizontalInsetPx = with(density) { 3.dp.toPx() }
        val tabWidthPx = (constraints.maxWidth.toFloat() - horizontalInsetPx * 2f) / tabCount
        val barWidthPx = constraints.maxWidth.toFloat()
        val barPressedGrowthPx = with(density) { 16.dp.toPx() }
        val glassEnabled = backdrop != null && capability != GlassCapability.STATIC
        val panelOffset = with(density) {
            val fraction = (offsetAnimation.value / barWidthPx).coerceIn(-1f, 1f)
            4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
        }

        val baseModifier = if (glassEnabled) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                layerBlock = {
                    val scale = 1f + barPressedGrowthPx * dragAnimation.pressProgress / size.width
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = { drawRect(dockContainerColor) }
            )
        } else {
            Modifier
                .graphicsLayer {
                    val scale = 1f + barPressedGrowthPx * dragAnimation.pressProgress / barWidthPx
                    scaleX = scale
                    scaleY = scale
                }
                .background(dockContainerColor, shape)
        }

        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .then(baseModifier)
                .height(54.dp)
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(colors.onSurfaceVariant, 1f, selectTab)
        }

        if (glassEnabled) {
            Row(
                modifier = Modifier
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * dragAnimation.pressProgress,
                                24.dp.toPx() * dragAnimation.pressProgress
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(dockContainerColor) }
                    )
                    .height(48.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content(colors.primary, lerp(1f, 1.2f, dragAnimation.pressProgress), selectTab)
            }
        }

        val selectorModifier = Modifier
            .padding(horizontal = 3.dp)
            .graphicsLayer {
                translationX = dragAnimation.value * tabWidthPx + panelOffset
            }
            .then(
                if (glassEnabled && combinedBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { shape },
                        effects = {
                            lens(
                                10.dp.toPx() * dragAnimation.pressProgress,
                                14.dp.toPx() * dragAnimation.pressProgress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                        },
                        shadow = {
                            Shadow(alpha = dragAnimation.pressProgress)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * dragAnimation.pressProgress,
                                alpha = dragAnimation.pressProgress
                            )
                        },
                        layerBlock = {
                            scaleX = dragAnimation.scaleX
                            scaleY = dragAnimation.scaleY
                            val velocity = dragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(
                                if (colors.isDark) Color.White.copy(alpha = 0.10f)
                                else Color.Black.copy(alpha = 0.10f),
                                alpha = 1f - dragAnimation.pressProgress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * dragAnimation.pressProgress))
                        }
                    )
                } else {
                    Modifier
                        .graphicsLayer {
                            scaleX = dragAnimation.scaleX
                            scaleY = dragAnimation.scaleY
                            val velocity = dragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        }
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.10f)
                            else Color.Black.copy(alpha = 0.10f),
                            shape
                        )
                        .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
                }
            )
            .height(48.dp)
            .fillMaxWidth(1f / tabCount)

        Box(selectorModifier)

        // A full-width gesture layer makes every tab react on ACTION_DOWN, not
        // only after the currently selected lens has started dragging.
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .pointerInput(tabWidthPx, tabCount, selectedIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        down.consume()
                        dragging = true
                        val pressedIndex = ((down.position.x - horizontalInsetPx) / tabWidthPx)
                            .toInt().coerceIn(0, tabCount - 1)
                        dragAnimation.press()
                        dragAnimation.movePressedToValue(pressedIndex.toFloat())

                        var completed = false
                        var cancelled = false
                        var totalDragX = 0f
                        var rawOffsetX = offsetAnimation.value
                        while (!completed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                completed = true
                                cancelled = true
                            } else if (!change.pressed) {
                                change.consume()
                                completed = true
                            } else {
                                val dragAmount = change.position.x - change.previousPosition.x
                                change.consume()
                                if (dragAmount != 0f) {
                                    totalDragX += dragAmount
                                    dragAnimation.updateValue(
                                        dragAnimation.targetValue + dragAmount / tabWidthPx
                                    )
                                    rawOffsetX += dragAmount
                                    scope.launch { offsetAnimation.snapTo(rawOffsetX) }
                                }
                            }
                        }

                        dragging = false
                        if (cancelled) {
                            dragAnimation.animateToValue(selectedIndex.toFloat())
                        } else {
                            val target = if (abs(totalDragX) < viewConfiguration.touchSlop) {
                                pressedIndex
                            } else {
                                dragAnimation.targetValue.roundToInt().coerceIn(0, tabCount - 1)
                            }
                            dragAnimation.animateToValue(target.toFloat())
                            onSelected(target)
                        }
                        scope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                    }
                }
        )
    }
}

/** Liquid icon control using the same Backdrop lens as the library's LiquidButton sample. */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = LiquidTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalNavigationGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
        label = "liquidActionPress"
    )
    val glassEnabled = backdrop != null && capability != GlassCapability.STATIC
    val surfaceModifier = if (glassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(2.dp.toPx())
                lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
            },
            highlight = { Highlight.Default.copy(alpha = 0.58f + pressProgress * 0.34f) },
            shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.12f)) },
            innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.14f + pressProgress * 0.34f) },
            layerBlock = {
                val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, pressProgress)
                scaleX = scale
                scaleY = scale
            },
            // The official LiquidButton leaves the surface untinted by default;
            // the refracted page and edge highlight form the glass material.
            onDrawSurface = { }
        ).border(BorderStroke(0.8.dp, colors.glassBorder), CircleShape)
    } else {
        Modifier
            .graphicsLayer {
                val scale = lerp(1f, 1.08f, pressProgress)
                scaleX = scale
                scaleY = scale
            }
            .background(
                if (colors.isDark) Color(0xFF121212).copy(alpha = 0.40f)
                else Color(0xFFFAFAFA).copy(alpha = 0.40f),
                CircleShape
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), CircleShape)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .then(surfaceModifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
