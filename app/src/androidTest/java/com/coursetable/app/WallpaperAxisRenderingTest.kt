package com.coursetable.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WallpaperAxisRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticTextSamplingFollowsTheVisibleCrop() {
        val image = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setPixel(x, y, if (x < 100) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            val c = WallpaperConfig("12345678-1234-1234-1234-123456789abc.png", mask = 0f, gridOpacity = 0f)
            val left = averageWallpaperColor(image, c.copy(positionX = 0f), 100f, 200f)
            val right = averageWallpaperColor(image, c.copy(positionX = 1f), 100f, 200f)
            assertEquals(Color.Black, left)
            assertEquals(Color.White, right)
            assertNotEquals(wallpaperTextColorOn(left), wallpaperTextColorOn(right))
        } finally { image.recycle() }
    }

    @Test fun axesStayReadableOnWhiteBlackAndBusyWallpaperInBothThemesAtZeroGridOpacity() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val config = mutableStateOf(WallpaperConfig("12345678-1234-1234-1234-123456789abc.png",
            mask = 0f, gridOpacity = 0f, blurDp = 0f))
        val bitmaps = listOf("white", "black", "checker").associateWith { name ->
            Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until height) for (x in 0 until width) {
                    setPixel(x, y, if (name == "white" || name == "checker" && (x / 6 + y / 6) % 2 == 0)
                        android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                }
            }
        }
        val bitmap = mutableStateOf(bitmaps.getValue("white"))
        var tapped = false
        val course = Course(id = 1, name = "对比度验证课程", dayOfWeek = 2, startSection = 1, duration = 2,
            startWeek = 1, endWeek = 18)
        compose.setContent {
            CourseTableTheme(mode.value) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalWallpaperActive provides true, LocalWallpaperCardOpacity provides 1f) {
                        Box(Modifier.fillMaxSize()) {
                            WallpaperBackground(config.value, bitmap.value, Modifier.fillMaxSize())
                            val settings = AppSettings(wallpaper = config.value)
                            TimetableGrid(0.dp, 0.dp, settings, LocalDate.of(2026, 10, 26), 1,
                                TimetableCourseIndex(listOf(course)).forWeek(1, true, WeekFilter.ALL), true, 1,
                                { tapped = true }, { _, _ -> }, null,
                                axisStyle = rememberWallpaperAxisStyle(config.value, bitmap.value))
                        }
                    }
                }
            }
        }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for ((name, image) in bitmaps) {
            compose.runOnIdle { mode.value = theme; bitmap.value = image }
            // Read the actual rendered text style, then compare it with captured pixels
            // across its background line. No assumption about which black/white is selected.
            for (text in listOf("周二", "10/27", "1", "08:00")) {
                val node = compose.onNodeWithText(text).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val foreground = layouts.single().layoutInput.style.color
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val x = (bounds.left - root.left - 2).toInt().coerceIn(0, pixels.width - 1)
                for (fraction in listOf(.1f, .5f, .9f)) {
                    val y = (bounds.top - root.top + bounds.height * fraction).toInt().coerceIn(0, pixels.height - 1)
                    assertTrue("$theme/$name/$text: actual rendered backing must protect small text",
                        textContrast(foreground, pixels[x, y]) >= 4.5f)
                }
            }
            captureWallpaperScreenshot("wallpaper-axis-${theme.key}-$name")
        }
        compose.onNodeWithText("对比度验证课程").performClick()
        compose.runOnIdle {
            assertTrue("Axis protection cannot intercept course taps", tapped)
            config.value = config.value.copy(textMode = WallpaperTextMode.LIGHT, textClarity = false)
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("08:00").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().layoutInput.style.color.red > .9f)
    }
}
