package com.coursetable.app.ui.liquid

import android.os.Build
import android.view.RoundedCorner
import android.view.ViewTreeObserver
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// MaterialBackAnimationHelper's progress curve. System progress is input, not visual progress.
// https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/motion/MaterialBackAnimationHelper.java
internal val BackPreviewEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal fun backPreviewProgress(progress: Float): Float =
    BackPreviewEasing.transform(if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f)

internal data class BackPageTransform(val scale: Float, val x: Float, val y: Float)

/** Whole-window preview, with the far edge kept on screen rather than dragged out of it. */
internal fun backPageTransform(
    width: Float,
    height: Float,
    density: Float,
    progress: Float,
    direction: Float,
    touchDeltaY: Float,
    exitProgress: Float
): BackPageTransform {
    if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite()) {
        return BackPageTransform(1f, 0f, 0f)
    }
    val exit = exitProgress.coerceIn(0f, 1f)
    val preview = backPreviewProgress(progress)
    if (preview == 0f && exit == 0f) return BackPageTransform(1f, 0f, 0f)
    val continuedPreview = preview + (1f - preview) * exit
    // Commit continues the current preview, with a small additional shrink and fade.
    // It never switches to a full-width sideways fling halfway through the gesture.
    val scale = 1f - 0.10f * continuedPreview - 0.01f * exit
    val gap = 8f * density
    val horizontalRoom = max(0f, width * (1f - scale) / 2f - gap)
    val verticalRoom = min(max(0f, height * (1f - scale) / 2f - gap), 24f * density)
    val delta = if (touchDeltaY.isFinite()) touchDeltaY else 0f
    val fractionY = (abs(delta) / height).coerceIn(0f, 1f)
    val deceleratedY = 1f - (1f - fractionY) * (1f - fractionY)
    return BackPageTransform(
        scale,
        horizontalRoom * continuedPreview * direction,
        verticalRoom * deceleratedY * sign(delta) * (1f - exit)
    )
}

@Immutable
internal data class BackWindowCorners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f
)

/** Physical pixel radii: do not substitute theme-card dp radii or multiply them by progress. */
@Composable
internal fun rememberBackWindowCorners(): BackWindowCorners {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var corners by remember(view, configuration) { mutableStateOf(BackWindowCorners()) }
    DisposableEffect(view, configuration) {
        fun refresh() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val insets = view.rootWindowInsets ?: return
                fun radius(position: Int) = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
                corners = BackWindowCorners(
                    radius(RoundedCorner.POSITION_TOP_LEFT),
                    radius(RoundedCorner.POSITION_TOP_RIGHT),
                    radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                    radius(RoundedCorner.POSITION_BOTTOM_LEFT)
                )
            }
        }
        // Insets may not exist at first composition. Refresh once attached/layout changes,
        // without taking over the window's existing inset listener or using hidden APIs.
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
        observer.addOnGlobalLayoutListener(listener)
        refresh()
        onDispose { if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener) }
    }
    return corners
}
