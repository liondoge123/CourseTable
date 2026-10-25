package com.coursetable.app.ui.liquid

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Rendering level used to keep the app usable on every supported Android version. */
enum class GlassCapability { STATIC, BLUR, FULL }

/** Semantic styles keep refraction restrained and consistent across the app. */
enum class GlassStyle { CHROME, CONTROL, OVERLAY }

internal enum class OverlayDestination { SHEET, DIALOG, MENU }

private const val OverlaySceneDepth = 8

internal val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalNavigationGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalTopChromeGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalOverlayGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalGlassCapability = staticCompositionLocalOf { GlassCapability.STATIC }

internal class GlassOverlayEntry(
    val id: Any,
    val destination: OverlayDestination,
    hidesDock: Boolean = destination == OverlayDestination.SHEET,
    val content: @Composable () -> Unit
) {
    var hidesDock by mutableStateOf(hidesDock)
    var backCommitted by mutableStateOf(false)
}

@Stable
internal class GlassOverlayController {
    private val _entries = mutableStateListOf<GlassOverlayEntry>()
    val entries: List<GlassOverlayEntry> get() = _entries
    private val dockBlockingPages = mutableStateListOf<Any>()
    val hidesDock: Boolean get() = dockBlockingPages.isNotEmpty() ||
        entries.any { it.hidesDock && !it.backCommitted }

    fun setPageDockHidden(id: Any, hidden: Boolean) {
        if (hidden) {
            if (id !in dockBlockingPages) dockBlockingPages += id
        } else dockBlockingPages.remove(id)
    }

    fun add(entry: GlassOverlayEntry) {
        if (_entries.none { it.id === entry.id }) _entries += entry
    }

    fun remove(entry: GlassOverlayEntry) {
        _entries.remove(entry)
    }
}

internal val LocalGlassOverlayController = staticCompositionLocalOf<GlassOverlayController?> { null }
internal val LocalGlassOverlayEntry = staticCompositionLocalOf<GlassOverlayEntry?> { null }

/**
 * Root liquid-glass host. The scene recorder is attached explicitly with
 * [glassBackdropSource], keeping foreground glass out of its own sample.
 */
@Composable
fun LiquidBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val navigationBackdrop = rememberLayerBackdrop()
    val topChromeBackdrop = rememberLayerBackdrop()
    val overlays = remember { GlassOverlayController() }
    val capability = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassCapability.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassCapability.BLUR
            else -> GlassCapability.STATIC
        }
    }
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalNavigationGlassBackdrop provides navigationBackdrop,
        LocalTopChromeGlassBackdrop provides topChromeBackdrop,
        LocalGlassCapability provides capability,
        LocalGlassOverlayController provides overlays
    ) {
        Box(modifier) {
            OverlaySceneStack(
                entries = overlays.entries,
                level = OverlaySceneDepth - 1,
                baseContent = {
                    Box(
                        if (overlays.entries.isNotEmpty()) {
                            Modifier.fillMaxSize().clearAndSetSemantics { }
                        } else {
                            Modifier.fillMaxSize()
                        },
                        content = content
                    )
                }
            )
        }
    }
}

/**
 * Records every presentation level separately. Each overlay samples the complete scene below
 * it while remaining outside its own recorder, so nested dialogs include their presenting sheet.
 */
@Composable
private fun BoxScope.OverlaySceneStack(
    entries: List<GlassOverlayEntry>,
    level: Int,
    baseContent: @Composable BoxScope.() -> Unit
) {
    if (level < 0) {
        baseContent()
        return
    }

    // Keep this hierarchy mounted even when no overlays are visible. Moving the base content to
    // a different slot would dispose and recreate its portal registrations on every presentation.
    val sceneBackdrop = rememberLayerBackdrop()
    val recordsThisLevel = entries.size > level
    val blurRadius = with(LocalDensity.current) { 40.dp.toPx() }
    val sceneBlur = remember(blurRadius) { BlurEffect(blurRadius, blurRadius, TileMode.Clamp) }
    SideEffect {
        // Blur the complete opaque scene before sampling a panel from it. Blurring
        // a padded panel crop introduces transparent pixels outside screen bounds,
        // which lets the original sharp scene show through at the left/bottom rim.
        sceneBackdrop.graphicsLayer.renderEffect = if (recordsThisLevel) sceneBlur else null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (recordsThisLevel) {
                    Modifier
                        .layerBackdrop(sceneBackdrop)
                        .clearAndSetSemantics { }
                } else {
                    Modifier
                }
            )
    ) {
        OverlaySceneStack(entries, level - 1, baseContent)
    }

    entries.getOrNull(level)?.let { entry ->
        key(entry.id) {
            CompositionLocalProvider(
                LocalOverlayGlassBackdrop provides sceneBackdrop,
                LocalGlassOverlayEntry provides entry,
                LocalOverlayBackActive provides (level == entries.lastIndex),
                LocalBackInOverlay provides true
            ) {
                entry.content()
            }
        }
    }
}

/** Attach only to the background and page-content layer, never to glass chrome. */
@Composable
fun Modifier.glassBackdropSource(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    return if (backdrop != null) layerBackdrop(backdrop) else this
}

/**
 * Record the rendered page for floating navigation glass. This is deliberately a
 * separate layer from [glassBackdropSource]: glass controls inside the page may
 * sample the ambient layer without making the navigation layer sample itself.
 */
@Composable
fun Modifier.navigationGlassBackdropSource(): Modifier {
    val backdrop = LocalNavigationGlassBackdrop.current
    return if (backdrop != null) layerBackdrop(backdrop) else this
}

/** Record scrollable page content for top chrome that floats above that content. */
@Composable
fun Modifier.topChromeGlassBackdropSource(): Modifier {
    val backdrop = LocalTopChromeGlassBackdrop.current
    return if (backdrop != null) layerBackdrop(backdrop) else this
}

/** Low-cost theme-derived scene that gives refraction meaningful, quiet colour. */
@Composable
fun LiquidAmbientBackground(modifier: Modifier = Modifier) {
    val colors = LiquidTheme.colorScheme
    Canvas(modifier.fillMaxSize()) {
        drawRect(colors.background)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.onSurface.copy(alpha = if (colors.isDark) 0.025f else 0.015f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.08f),
                radius = size.maxDimension * 0.72f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.surfaceContainerHighest.copy(alpha = if (colors.isDark) 0.12f else 0.25f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.82f),
                radius = size.maxDimension * 0.68f
            )
        )
    }
}

@Composable
internal fun GlassOverlayPortal(
    destination: OverlayDestination = OverlayDestination.DIALOG,
    hidesDock: Boolean = destination == OverlayDestination.SHEET,
    content: @Composable () -> Unit
) {
    if (!LocalBackLayerActive.current) return
    val controller = LocalGlassOverlayController.current
    val currentContent = rememberUpdatedState(content)
    if (controller == null) {
        content()
        return
    }
    val entry = remember(controller, destination) {
        GlassOverlayEntry(Any(), destination, hidesDock) { currentContent.value.invoke() }
    }
    SideEffect { entry.hidesDock = hidesDock }
    DisposableEffect(controller, entry) {
        controller.add(entry)
        onDispose { controller.remove(entry) }
    }
}
