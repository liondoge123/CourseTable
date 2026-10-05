package com.coursetable.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

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
    selectableIndices: List<Int> = List(tabCount) { it },
    drawSurface: Boolean = true,
    actionIndex: Int? = null,
    onAction: (() -> Unit)? = null,
    actionContentDescription: String? = null,
    content: @Composable RowScope.(contentColor: Color, itemScale: Float, selectTab: (Int) -> Unit) -> Unit
) {
    require(tabCount > 0)
    require(selectableIndices.isNotEmpty())
    require(selectedIndex in selectableIndices)
    require(selectableIndices.all { it in 0 until tabCount })
    require(actionIndex == null || actionIndex in 0 until tabCount)
    require((actionIndex == null) == (onAction == null))
    val backdrop = LocalNavigationGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current
    val colors = LiquidTheme.colorScheme
    val shape = Capsule()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetAnimation = remember { Animatable(0f) }
    val actionInteractionSource = remember { MutableInteractionSource() }
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
        if (index in selectableIndices) {
            dragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    fun nearestSelectableIndex(value: Float): Int = selectableIndices.minBy { index ->
        abs(index - value)
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
                .then(if (drawSurface) baseModifier else Modifier)
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
                            val selectableTarget = nearestSelectableIndex(target.toFloat())
                            dragAnimation.animateToValue(selectableTarget.toFloat())
                            onSelected(selectableTarget)
                        }
                        scope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                    }
                }
        )

        if (actionIndex != null && onAction != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .graphicsLayer {
                        translationX = actionIndex * tabWidthPx + panelOffset
                    }
                    .height(54.dp)
                    .fillMaxWidth(1f / tabCount)
                    .semantics {
                        role = Role.Button
                        if (actionContentDescription != null) {
                            contentDescription = actionContentDescription
                        }
                    }
                    .clickable(
                        interactionSource = actionInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onAction
                    )
            )
        }
    }
}

/** Liquid icon control using the same Backdrop lens as the library's LiquidButton sample. */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = LiquidTheme.colorScheme.primary,
    shape: Shape = CircleShape,
    containerColor: Color? = null,
    contentDescription: String? = null,
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
            shape = { shape },
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
            // Keep the official material untinted by default; callers may add a
            // translucent accent surface without changing the optical effects.
            onDrawSurface = {
                if (containerColor != null) drawRect(containerColor)
            }
        ).border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    } else {
        Modifier
            .graphicsLayer {
                val scale = lerp(1f, 1.08f, pressProgress)
                scaleX = scale
                scaleY = scale
            }
            .background(
                containerColor ?: if (colors.isDark) Color(0xFF121212).copy(alpha = 0.40f)
                else Color(0xFFFAFAFA).copy(alpha = 0.40f),
                shape
            )
            .border(BorderStroke(0.8.dp, colors.glassBorder), shape)
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .then(surfaceModifier)
                .semantics {
                    role = Role.Button
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Mathematically exact closed 2D shape for a liquid metaball dock.
 *
 * - When hidden ([progress] <= 0.02f): returns the single capsule dock.
 * - When budding/bridging (0.02f < [progress] < [snapProgress]):
 *   Connects the main capsule body to the emerging bulb via smooth concave
 *   circular tangent fillets that stretch and thin out, forming an authentic
 *   liquid metaball bridge matching the fluid stretching in reference images.
 * - When detached ([progress] >= [snapProgress]):
 *   The liquid bridge pinches off / breaks, and the shape cleanly separates
 *   into two distinct independent closed paths:
 *   1. The main dock capsule [0, dockWidth]
 *   2. The circular companion button [bulbCenterX - r, bulbCenterX + r]
 *   leaving a completely clean gap between them in the resting default state.
 */
class LiquidMetaballDockShape(
    private val progress: Float,
    private val dockWidth: Float,
    private val dockHeight: Float,
    private val maxBulbDistance: Float,
    private val bulbRadius: Float,
    private val snapProgress: Float = 0.70f,
    private val pressScale: Float = 1f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val h = dockHeight
        val r1 = h / 2f
        val yc = h / 2f
        val x1 = dockWidth - r1

        // 1. Fully retracted / hidden
        if (progress <= 0.02f) {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = dockWidth,
                        bottom = h,
                        radiusX = r1,
                        radiusY = r1
                    )
                )
            }
            return Outline.Generic(path)
        }

        val dTouch = 2f * r1
        val dSnap = dTouch + with(density) { 9.dp.toPx() }

        // Compute current center-to-center distance d
        val d = if (progress < snapProgress) {
            val t = (progress / snapProgress).coerceIn(0f, 1f)
            if (t <= 0.42f) {
                val subT = t / 0.42f
                lerp(0f, dTouch, subT)
            } else {
                val subT = (t - 0.42f) / 0.58f
                lerp(dTouch, dSnap, subT)
            }
        } else {
            val t = ((progress - snapProgress) / (1f - snapProgress)).coerceAtLeast(0f)
            lerp(dSnap, maxBulbDistance, t)
        }

        val x2 = x1 + d

        // 2. Detached / resting state (bridge has snapped cleanly)
        if (progress >= snapProgress) {
            val path = Path().apply {
                // Dock capsule
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = dockWidth,
                        bottom = h,
                        radiusX = r1,
                        radiusY = r1
                    )
                )
                // Detached circular action button
                val currentBulbRadius = bulbRadius * pressScale
                addOval(
                    Rect(
                        left = x2 - currentBulbRadius,
                        top = yc - currentBulbRadius,
                        right = x2 + currentBulbRadius,
                        bottom = yc + currentBulbRadius
                    )
                )
            }
            return Outline.Generic(path)
        }

        // 3. Bridging / stretching metaball state (0.02 < progress < snapProgress)
        val t = (progress / snapProgress).coerceIn(0f, 1f)
        val r = bulbRadius
        val rfSnap = with(density) { 4.9.dp.toPx() }
        val rfInitial = with(density) { 22.dp.toPx() }
        val rf = if (t <= 0.42f) {
            rfInitial
        } else {
            val subT = (t - 0.42f) / 0.58f
            lerp(rfInitial, rfSnap, subT)
        }

        val dHalf = d / 2f
        val sumR = r + rf
        val valSqrt = sumR * sumR - dHalf * dHalf

        if (valSqrt <= 0f) {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = dockWidth,
                        bottom = h,
                        radiusX = r1,
                        radiusY = r1
                    )
                )
                addOval(
                    Rect(
                        left = x2 - bulbRadius,
                        top = yc - bulbRadius,
                        right = x2 + bulbRadius,
                        bottom = yc + bulbRadius
                    )
                )
            }
            return Outline.Generic(path)
        }

        val hDist = kotlin.math.sqrt(valSqrt)
        val sinA = (dHalf / sumR).coerceIn(-1f, 1f)
        val arcAngleDeg = Math.toDegrees(kotlin.math.asin(sinA.toDouble())).toFloat()
        val xf = x1 + dHalf

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(x1, 0f)

            // Convex arc on circle 1 (dock cap)
            arcTo(
                rect = Rect(x1 - r, yc - r, x1 + r, yc + r),
                startAngleDegrees = 270f,
                sweepAngleDegrees = arcAngleDeg,
                forceMoveTo = false
            )

            // Concave fillet on top fillet circle (the stretching liquid waist)
            arcTo(
                rect = Rect(xf - rf, yc - hDist - rf, xf + rf, yc - hDist + rf),
                startAngleDegrees = 90f + arcAngleDeg,
                sweepAngleDegrees = -2f * arcAngleDeg,
                forceMoveTo = false
            )

            // Convex arc around circle 2 (the companion bulb)
            arcTo(
                rect = Rect(x2 - r, yc - r, x2 + r, yc + r),
                startAngleDegrees = 270f - arcAngleDeg,
                sweepAngleDegrees = 180f + 2f * arcAngleDeg,
                forceMoveTo = false
            )

            // Concave fillet on bottom fillet circle (the stretching liquid waist)
            arcTo(
                rect = Rect(xf - rf, yc + hDist - rf, xf + rf, yc + hDist + rf),
                startAngleDegrees = 270f + arcAngleDeg,
                sweepAngleDegrees = -2f * arcAngleDeg,
                forceMoveTo = false
            )

            // Convex arc on circle 1 back to bottom
            arcTo(
                rect = Rect(x1 - r, yc - r, x1 + r, yc + r),
                startAngleDegrees = 90f - arcAngleDeg,
                sweepAngleDegrees = arcAngleDeg,
                forceMoveTo = false
            )

            lineTo(r, h)

            // Semicircle around left dock cap back to top
            arcTo(
                rect = Rect(0f, 0f, 2f * r, h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A combined liquid dock container that hosts a 4-tab [LiquidNavigationTabs] and an integrated
 * action button (like "+") on the same row. When [actionVisible] is toggled, the companion
 * bulb dynamically extrudes out from / retracts into the dock's right edge using an authentic
 * liquid metaball animation (smooth concave fillet neck that stretches and thins out), pinches
 * off cleanly, and settles as a completely detached companion button with a clear gap in the
 * resting default state.
 */
@Composable
fun LiquidMetaballNavigationDock(
    actionVisible: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionContentDescription: String? = "添加课程",
    actionIcon: @Composable BoxScope.() -> Unit = {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = LiquidTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    },
    tabs: @Composable (dockWidth: Dp, drawSurface: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val colors = LiquidTheme.colorScheme
    val backdrop = LocalNavigationGlassBackdrop.current ?: LocalGlassBackdrop.current
    val capability = LocalGlassCapability.current

    val dockHeightDp = 54.dp
    val bulbRadiusDp = 27.dp
    val bulbSizeDp = 54.dp
    val restingGapDp = 12.dp
    val snapProgress = 0.70f
    val maxBulbDistanceDp = bulbRadiusDp * 2f + restingGapDp
    val dTouchDp = bulbRadiusDp * 2f
    val dSnapDp = dTouchDp + 9.dp

    val progress by animateFloatAsState(
        targetValue = if (actionVisible) 1f else 0f,
        animationSpec = if (actionVisible) {
            spring(dampingRatio = 0.74f, stiffness = 180f)
        } else {
            tween(durationMillis = 340, easing = FastOutSlowInEasing)
        },
        label = "metaballActionProgress"
    )

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxAvailWidthDp = with(density) { constraints.maxWidth.toDp() }
        val maxExtraWidthDp = maxBulbDistanceDp
        val dockWidthDp = (maxAvailWidthDp - maxExtraWidthDp).coerceIn(248.dp, 280.dp)

        val dockWidthPx = with(density) { dockWidthDp.toPx() }
        val dockHeightPx = with(density) { dockHeightDp.toPx() }
        val bulbRadiusPx = with(density) { bulbRadiusDp.toPx() }
        val maxBulbDistancePx = with(density) { maxBulbDistanceDp.toPx() }

        val currentDDp = if (progress < snapProgress) {
            val t = (progress / snapProgress).coerceIn(0f, 1f)
            if (t <= 0.42f) {
                val subT = t / 0.42f
                (dTouchDp.value * subT).dp
            } else {
                val subT = (t - 0.42f) / 0.58f
                (dTouchDp.value + (dSnapDp.value - dTouchDp.value) * subT).dp
            }
        } else {
            val t = ((progress - snapProgress) / (1f - snapProgress)).coerceAtLeast(0f)
            (dSnapDp.value + (maxBulbDistanceDp.value - dSnapDp.value) * t).dp
        }

        val totalWidthDp = dockWidthDp + currentDDp

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "plusPress"
        )

        val metaballShape = remember(
            progress,
            dockWidthPx,
            dockHeightPx,
            maxBulbDistancePx,
            bulbRadiusPx,
            pressScale,
            density
        ) {
            LiquidMetaballDockShape(
                progress = progress,
                dockWidth = dockWidthPx,
                dockHeight = dockHeightPx,
                maxBulbDistance = maxBulbDistancePx,
                bulbRadius = bulbRadiusPx,
                snapProgress = snapProgress,
                pressScale = pressScale
            )
        }

        val dockContainerColor = if (colors.isDark) {
            Color(0xFF121212).copy(alpha = 0.40f)
        } else {
            Color(0xFFFAFAFA).copy(alpha = 0.40f)
        }

        val isDetached = progress >= snapProgress
        val isHidden = progress <= 0.02f
        val isBridging = !isDetached && !isHidden

        val glassEnabled = backdrop != null && capability != GlassCapability.STATIC

        val bridgeBorderColor = if (colors.isDark) {
            Color.White.copy(alpha = 0.50f)
        } else {
            Color.White.copy(alpha = 0.85f)
        }
        val bridgeBorderStroke = BorderStroke(1.2.dp, bridgeBorderColor)

        val bridgingModifier = if (isBridging) {
            if (glassEnabled) {
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { metaballShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.60f) },
                        shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.12f)) },
                        onDrawSurface = { drawRect(dockContainerColor) }
                    )
                    .border(bridgeBorderStroke, metaballShape)
            } else {
                Modifier
                    .background(dockContainerColor, metaballShape)
                    .border(bridgeBorderStroke, metaballShape)
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .width(totalWidthDp)
                .height(dockHeightDp)
                .then(bridgingModifier)
        ) {
            // 1. Navigation tabs (draws full refraction lens when detached or hidden; during bridging, surface is drawn by the metaball container)
            Box(
                modifier = Modifier
                    .width(dockWidthDp)
                    .fillMaxHeight()
            ) {
                tabs(dockWidthDp, isDetached || isHidden)
            }

            // 2. The Plus companion button
            if (progress > 0.05f) {
                val bulbCenterXDp = dockWidthDp - bulbRadiusDp + currentDDp
                val bulbLeftXDp = bulbCenterXDp - bulbRadiusDp
                val bulbTopYDp = (dockHeightDp - bulbSizeDp) / 2f

                if (isDetached) {
                    // Fully detached resting state: authentic liquid glass button with chromatic lens, highlight, and inner shadow
                    LiquidGlassIconButton(
                        onClick = onActionClick,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = with(density) { bulbLeftXDp.toPx().roundToInt() },
                                    y = with(density) { bulbTopYDp.toPx().roundToInt() }
                                )
                            }
                            .size(bulbSizeDp),
                        contentColor = colors.primary,
                        containerColor = dockContainerColor,
                        contentDescription = actionContentDescription,
                        shape = CircleShape
                    ) {
                        val iconAppearAlpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = iconAppearAlpha
                            },
                            contentAlignment = Alignment.Center,
                            content = actionIcon
                        )
                    }
                } else {
                    // Bridging transition state: rendered on the unified metaball bridge
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = with(density) { bulbLeftXDp.toPx().roundToInt() },
                                    y = with(density) { bulbTopYDp.toPx().roundToInt() }
                                )
                            }
                            .size(bulbSizeDp)
                            .semantics {
                                role = Role.Button
                                if (actionContentDescription != null) {
                                    contentDescription = actionContentDescription
                                }
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onActionClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconAppearAlpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = iconAppearAlpha
                                scaleX = lerp(0.6f, 1f, iconAppearAlpha) * pressScale
                                scaleY = lerp(0.6f, 1f, iconAppearAlpha) * pressScale
                            },
                            contentAlignment = Alignment.Center,
                            content = actionIcon
                        )
                    }
                }
            }
        }
    }
}


