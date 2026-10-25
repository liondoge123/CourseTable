package com.coursetable.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.coursetable.app.ui.liquid.GlassOverlayPortal
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.OverlayDestination
import com.coursetable.app.ui.liquid.OverlayGlassStyle
import com.coursetable.app.ui.liquid.OverlayGlassSurface
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Render-clock pixel checks: sharp background detail must not leak at screen edges. */
@SdkSuppress(minSdkVersion = 31)
class OverlayBlurEdgeTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(mode: ThemeMode) {
        var cell = 1
        compose.setContent {
            CourseTableTheme(themeMode = mode) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        cell = 6.dp.roundToPx().coerceAtLeast(1)
                        for (y in 0..(size.height.toInt() / cell)) {
                            for (x in 0..(size.width.toInt() / cell)) {
                                drawRect(
                                    if ((x + y) % 2 == 0) Color.Black else Color.White,
                                    Offset((x * cell).toFloat(), (y * cell).toFloat()),
                                    Size(cell.toFloat(), cell.toFloat())
                                )
                            }
                        }
                    }
                    GlassOverlayPortal(OverlayDestination.SHEET) {
                        OverlayGlassSurface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RectangleShape,
                            shadowElevation = 0.dp,
                            style = OverlayGlassStyle.SHEET
                        ) { }
                    }
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("overlay-glass-sheet").captureToImage().toPixelMap()
        val inset = (cell * 2 / 3).coerceAtLeast(1)
        fun verticalDetail(x: Int): Float =
            (pixels.height / 4 until pixels.height * 3 / 4 - cell).map { y ->
                abs(pixels[x, y].luminance() - pixels[x, y + cell].luminance())
            }.average().toFloat()
        fun horizontalDetail(y: Int): Float =
            (pixels.width / 4 until pixels.width * 3 / 4 - cell).map { x ->
                abs(pixels[x, y].luminance() - pixels[x + cell, y].luminance())
            }.average().toFloat()

        val centerVertical = verticalDetail(pixels.width / 2)
        val centerHorizontal = horizontalDetail(pixels.height / 2)
        for (x in listOf(inset, pixels.width - 1 - inset)) {
            assertTrue("Side edge must suppress checkerboard detail like the center",
                verticalDetail(x) <= centerVertical + 0.03f)
        }
        for (y in listOf(inset, pixels.height - 1 - inset)) {
            assertTrue("Top/bottom edge must suppress checkerboard detail like the center",
                horizontalDetail(y) <= centerHorizontal + 0.03f)
        }
    }

    @Test fun lightGlassDoesNotLeakSharpDetailAtScreenEdges() = verify(ThemeMode.LIGHT)
    @Test fun darkGlassDoesNotLeakSharpDetailAtScreenEdges() = verify(ThemeMode.DARK)
}
