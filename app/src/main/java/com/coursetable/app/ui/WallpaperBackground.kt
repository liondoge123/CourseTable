package com.coursetable.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.coursetable.app.CourseApp
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperRepository
import com.coursetable.app.ui.liquid.LiquidAmbientBackground
import com.coursetable.app.ui.theme.LiquidTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

internal val LocalWallpaperActive = compositionLocalOf { false }
internal val LocalWallpaperCardOpacity = compositionLocalOf { 1f }

internal data class WallpaperPlacement(val origin: Offset, val size: Size)

internal fun wallpaperPlacement(imageWidth: Int, imageHeight: Int, width: Float, height: Float,
                                config: WallpaperConfig): WallpaperPlacement {
    val c = config.normalized()
    val scale = max(width / imageWidth, height / imageHeight) * c.scale
    val size = Size(imageWidth * scale, imageHeight * scale)
    return WallpaperPlacement(Offset(-(size.width - width) * c.positionX, -(size.height - height) * c.positionY), size)
}

@Composable
internal fun rememberWallpaperBitmap(config: WallpaperConfig?, repository: WallpaperRepository =
    (LocalContext.current.applicationContext as CourseApp).wallpaperRepository): Bitmap? = rememberWallpaperImage(config, repository).bitmap

internal data class WallpaperImage(val bitmap: Bitmap?, val loading: Boolean)

@Composable
internal fun rememberWallpaperImage(config: WallpaperConfig?, repository: WallpaperRepository): WallpaperImage {
    val viewport = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    // Position and mask updates must not restart decoding or filtering.
    val key = config?.let { listOf(it.imageId, it.blurDp, it.scale, viewport, density) }
    val loaded by produceState<Triple<String?, Bitmap?, Any?>>(Triple(null, null, null), key, repository) {
        val bitmap = try {
            config?.let { repository.bitmap(it, viewport.width.coerceAtLeast(1) / density, viewport.height.coerceAtLeast(1) / density) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        value = Triple(config?.imageId, bitmap, key)
    }
    // An earlier image must never be drawn with a newly selected image's crop.
    return WallpaperImage(if (loaded.first == config?.imageId) loaded.second else null, config != null && loaded.third != key)
}

@Composable
internal fun WallpaperBackground(config: WallpaperConfig?, bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (config == null || bitmap == null) {
        LiquidAmbientBackground(modifier)
        return
    }
    val colors = LiquidTheme.colorScheme
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier.fillMaxSize()) {
        drawRect(colors.background)
        val placement = wallpaperPlacement(bitmap.width, bitmap.height, size.width, size.height, config)
        drawImage(image, dstOffset = IntOffset(placement.origin.x.roundToInt(), placement.origin.y.roundToInt()),
            dstSize = IntSize(placement.size.width.roundToInt().coerceAtLeast(1), placement.size.height.roundToInt().coerceAtLeast(1)))
        drawRect(colors.background.copy(alpha = config.mask))
    }
}
