package com.coursetable.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.theme.LiquidTheme
import kotlin.math.max
import kotlin.math.min

internal data class WallpaperAxisStyle(val foreground: Color, val scrim: Color)

private val DarkAxisText = Color(0xFF17191F)
private val LightAxisText = Color(0xFFF7F8FC)

internal fun textContrast(foreground: Color, background: Color): Float {
    val a = foreground.compositeOver(background).luminance()
    val b = background.luminance()
    return (max(a, b) + .05f) / (min(a, b) + .05f)
}

internal fun wallpaperTextColorOn(background: Color): Color {
    val preferred = if (textContrast(DarkAxisText, background) >= textContrast(LightAxisText, background)) DarkAxisText else LightAxisText
    if (textContrast(preferred, background) >= 4.5f) return preferred
    return if (textContrast(Color.Black, background) >= textContrast(Color.White, background)) Color.Black else Color.White
}

/** Match WallpaperBackground's mask followed by the timetable's translucent surface. */
internal fun wallpaperGridBackground(pixel: Color, config: WallpaperConfig, themeBackground: Color, surface: Color): Color =
    surface.copy(alpha = config.gridOpacity).compositeOver(
        themeBackground.copy(alpha = config.mask).compositeOver(pixel.compositeOver(themeBackground)))

internal fun wallpaperAxisStyle(config: WallpaperConfig, average: Color, themeBackground: Color, surface: Color): WallpaperAxisStyle {
    val c = config.normalized()
    val foreground = when (c.textMode) {
        WallpaperTextMode.AUTO -> wallpaperTextColorOn(wallpaperGridBackground(average, c, themeBackground, surface))
        WallpaperTextMode.DARK -> DarkAxisText
        WallpaperTextMode.LIGHT -> LightAxisText
    }
    if (!c.textClarity) return WallpaperAxisStyle(foreground, Color.Transparent)
    val backing = if (foreground.luminance() < .5f) Color.White else Color(0xFF15171D)
    // Samples choose the text polarity only. Protect against BOTH possible wallpaper
    // extremes, including detail the sampling missed and pixels exposed by scrolling.
    val extremes = listOf(Color.Black, Color.White).map { wallpaperGridBackground(it, c, themeBackground, surface) }
    fun sufficient(alpha: Float) = extremes.all {
        textContrast(foreground, backing.copy(alpha = alpha).compositeOver(it)) >= 5f
    }
    if (sufficient(0f)) return WallpaperAxisStyle(foreground, Color.Transparent)
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (sufficient(mid)) high = mid else low = mid
    }
    return WallpaperAxisStyle(foreground, backing.copy(alpha = (high + .01f).coerceAtMost(1f)))
}

/** Small fixed sample of the visible crop; reuse the already decoded/blurred bitmap. */
internal fun averageWallpaperColor(bitmap: Bitmap, config: WallpaperConfig, width: Float, height: Float): Color {
    val placement = wallpaperPlacement(bitmap.width, bitmap.height, width, height, config)
    var red = 0f; var green = 0f; var blue = 0f; var alpha = 0f
    val columns = 12
    val rows = 20
    repeat(rows) { row -> repeat(columns) { column ->
        val x = (((column + .5f) * width / columns - placement.origin.x) / placement.size.width * bitmap.width)
            .toInt().coerceIn(0, bitmap.width - 1)
        val y = (((row + .5f) * height / rows - placement.origin.y) / placement.size.height * bitmap.height)
            .toInt().coerceIn(0, bitmap.height - 1)
        val pixel = Color(bitmap.getPixel(x, y))
        red += pixel.red * pixel.alpha; green += pixel.green * pixel.alpha; blue += pixel.blue * pixel.alpha
        alpha += pixel.alpha
    } }
    return if (alpha == 0f) Color.Transparent else Color(red / alpha, green / alpha, blue / alpha, alpha / (columns * rows))
}

@Composable
internal fun rememberWallpaperAxisStyle(config: WallpaperConfig?, bitmap: Bitmap?): WallpaperAxisStyle? {
    val viewport = LocalWindowInfo.current.containerSize
    val colors = LiquidTheme.colorScheme
    if (config == null || bitmap == null || viewport.width <= 0 || viewport.height <= 0) return null
    val average = remember(bitmap, config.positionX, config.positionY, config.scale, viewport) {
        averageWallpaperColor(bitmap, config, viewport.width.toFloat(), viewport.height.toFloat())
    }
    return remember(config, average, colors.background, colors.surface) {
        wallpaperAxisStyle(config, average, colors.background, colors.surface)
    }
}
