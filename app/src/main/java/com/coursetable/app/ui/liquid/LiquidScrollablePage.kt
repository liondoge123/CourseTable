package com.coursetable.app.ui.liquid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/** Insets belong to scroll content, so its viewport reaches the physical window edge. */
@Composable
internal fun LiquidScrollablePage(
    modifier: Modifier = Modifier,
    topBarPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    floatingTopBar: Boolean = false,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberLayerBackdrop()
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val safe = insets.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(64.dp) }
    val topHeight = safe.calculateTopPadding() + if (topBar == null) 0.dp else barHeight
    val statusHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val contentPadding = PaddingValues(
        start = safe.calculateStartPadding(direction),
        top = topHeight,
        end = safe.calculateEndPadding(direction),
        bottom = safe.calculateBottomPadding()
    )
    // Floating capsules use the original page sampling; ordinary headers stay on ambient colour.
    CompositionLocalProvider(LocalTopChromeGlassBackdrop provides if (floatingTopBar) backdrop else chromeBackdrop) {
        Box(modifier.fillMaxSize().clipToBounds()) {
            // This recorder excludes both the fading material and the controls above it.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).consumeWindowInsets(insets)) {
                LiquidAmbientBackground(Modifier.layerBackdrop(chromeBackdrop))
                content(contentPadding)
            }
            if (topBar != null) {
                // Repaint only the fixed header strip using the same full-window background.
                // This hides scrolling rows without adding any blur or a separate panel edge.
                val headerTop = with(density) { safe.calculateTopPadding().toPx() }
                val headerBottom = with(density) { topHeight.toPx() }
                if (!floatingTopBar) LiquidAmbientBackground(Modifier.drawWithContent {
                    clipRect(top = headerTop, bottom = headerBottom) {
                        this@drawWithContent.drawContent()
                    }
                })
                Box(Modifier.fillMaxWidth().padding(
                    start = safe.calculateStartPadding(direction),
                    top = safe.calculateTopPadding(),
                    end = safe.calculateEndPadding(direction)
                )) {
                    Box(Modifier.fillMaxWidth().onSizeChanged {
                        barHeight = with(density) { it.height.toDp() }
                    }.pointerInput(Unit) {
                        // Empty toolbar areas must not activate a row scrolling underneath.
                        detectTapGestures(onTap = {})
                    }.padding(topBarPadding)) { topBar() }
                }
            }
            if (statusHeight > 0.dp) LiquidTopEdge(backdrop, statusHeight)
        }
    }
}

/** Shared list/column padding, applied AFTER scrolling rather than to its viewport. */
@Composable
internal fun PaddingValues.pagePadding(
    horizontal: Dp = 16.dp,
    vertical: Dp = 16.dp,
    extraBottom: Dp = 0.dp
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        top = calculateTopPadding() + vertical,
        end = calculateEndPadding(direction) + horizontal,
        bottom = calculateBottomPadding() + vertical + extraBottom
    )
}

/** Blur and its fade are confined to the system status bar, never the app's header. */
@Composable
private fun LiquidTopEdge(backdrop: LayerBackdrop, statusHeight: Dp) {
    val colors = LiquidTheme.colorScheme
    val capability = LocalGlassCapability.current
    val fadeHeight = minOf(16.dp, statusHeight)
    val fraction = ((statusHeight - fadeHeight) / statusHeight).coerceIn(0f, .99f)
    val material = if (capability != GlassCapability.STATIC) Modifier.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        // Sample beyond the visible strip. Clamping to its first/last row would
        // stretch a passing glyph into a large dark patch, then abruptly remove it.
        effects = { blur(18.dp.toPx()); vibrancy(); padding = 36.dp.toPx() },
        onDrawBackdrop = { drawBackdrop ->
            // The source ends at the physical window edge. Fill the overscan first,
            // so out-of-window samples stay opaque without repeating moving pixels.
            val overscan = 36.dp.toPx()
            drawRect(colors.background, topLeft = Offset(-overscan, -overscan), size = size)
            drawBackdrop()
        },
        onDrawSurface = {
            drawRect(colors.background.copy(alpha = if (colors.isDark) .18f else .22f))
            drawRect(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (colors.isDark) .025f else .08f), Color.Transparent
            )))
        }
    ) else Modifier.drawWithContent {
        drawRect(colors.background.copy(alpha = .94f))
        drawContent()
    }
    Box(Modifier.fillMaxWidth().height(statusHeight)
        .testTag("liquid-top-edge")
        .clearAndSetSemantics { }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(
                0f to Color.White,
                fraction to Color.White,
                1f to Color.Transparent
            ), blendMode = BlendMode.DstIn)
        }.then(material))
}

@Composable
internal fun LiquidPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Spacer(Modifier.width(4.dp))
        Text(title, modifier = Modifier.weight(1f), style = LiquidTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}
