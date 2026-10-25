package com.coursetable.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.ui.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WallpaperCardTransparencyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun courseCardTransparencyChangesOnlyItsBackgroundAndKeepsTapTarget() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        val config = WallpaperConfig("12345678-1234-1234-1234-123456789abc.png", blurDp = 0f, mask = 0f)
        var opacity by mutableFloatStateOf(1f)
        var clicks = 0
        compose.setContent {
            CourseTableTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalWallpaperCardOpacity provides opacity, LocalWallpaperActive provides true) {
                    Box(Modifier.fillMaxSize()) {
                        WallpaperBackground(config, bitmap)
                        TimetableCourseCardSurface("高等数学", "教师", "教学楼 302", Color.Cyan, 2,
                            Modifier.align(Alignment.Center).size(150.dp, 180.dp).testTag("transparent-card"),
                            onClick = { clicks++ })
                    }
                }
            }
        }
        val opaque = compose.onNodeWithTag("transparent-card").captureToImage().toPixelMap()
        assertTrue(opaque[(opaque.width * .8f).toInt(), (opaque.height * .8f).toInt()].luminance() > .5f)
        compose.runOnIdle { opacity = 0f }
        val clear = compose.onNodeWithTag("transparent-card").captureToImage().toPixelMap()
        assertTrue(clear[(clear.width * .8f).toInt(), (clear.height * .8f).toInt()].luminance() < .1f)
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithTag("transparent-card").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
