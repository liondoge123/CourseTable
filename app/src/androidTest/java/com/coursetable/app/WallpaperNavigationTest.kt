package com.coursetable.app

import androidx.activity.BackEventCompat
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WallpaperNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun wallpaperStaysFixedWhileScrollingAndDoesNotLeakIntoOtherPages() = runBlocking {
        val app = compose.activity.application as CourseApp
        val original = app.settingsRepository.wallpaper.first()
        WallpaperTestEnvironment().use { env ->
            val wallpaper = app.wallpaperRepository.prepare(android.net.Uri.fromFile(env.patternedSource()))
            try {
                // Preserve an existing user's wallpaper file while temporarily changing the preference.
                app.settingsRepository.saveWallpaper(wallpaper)
                compose.onAllNodesWithText("课表")[0].performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("timetable-wallpaper-background").fetchSemanticsNodes().isNotEmpty() }
                val before = compose.onNodeWithTag("timetable-wallpaper-background").captureToImage().toPixelMap()
                captureWallpaperScreenshot("wallpaper-home")
                compose.onNodeWithTag("timetable-scroll").performTouchInput { swipeUp() }
                val after = compose.onNodeWithTag("timetable-wallpaper-background").captureToImage().toPixelMap()
                captureWallpaperScreenshot("wallpaper-home-scrolled")
                for (fraction in listOf(.25f, .5f, .75f)) {
                    val y = (before.height * fraction).toInt()
                    assertEquals("Wallpaper must stay fixed while the grid scrolls", before[1, y], after[1, y])
                }
                compose.onNodeWithContentDescription("下一周").performClick()
                compose.onNodeWithTag("timetable-wallpaper-background").assertIsDisplayed()
                compose.onAllNodesWithText("设置")[0].performClick()
                compose.onNodeWithTag("timetable-wallpaper-background").assertDoesNotExist()
                compose.onNodeWithText("外观设置").performScrollTo().performClick()
                compose.onNodeWithText("课表壁纸").performScrollTo().performClick()
                compose.mainClock.advanceTimeBy(350)
                compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
                captureWallpaperScreenshot("wallpaper-editor-full")
                compose.onNodeWithTag("wallpaper-adjust").performClick()
                compose.mainClock.advanceTimeBy(350)
                compose.onNodeWithTag("wallpaper-slider-遮罩")
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.65f) }
                compose.activityRule.scenario.recreate()
                compose.onNodeWithTag("wallpaper-slider-遮罩")
                    .assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.65f, 0f..0.8f))
                compose.onNodeWithTag("wallpaper-adjustments-done").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("wallpaper-adjustments").fetchSemanticsNodes().isEmpty() }
                compose.runOnIdle {
                    compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
                }
                compose.runOnIdle {
                    compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(180f, 300f, .6f, BackEventCompat.EDGE_LEFT))
                }
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
                compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
                assertEquals(wallpaper, app.settingsRepository.wallpaper.first())
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithTag("wallpaper-editor").assertDoesNotExist()
                compose.onNodeWithTag("appearance-settings-page").assertIsDisplayed()
                compose.onNodeWithContentDescription("返回").performClick()
                compose.onAllNodesWithText("课表")[0].performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("timetable-wallpaper-background").fetchSemanticsNodes().isNotEmpty() }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("timetable-wallpaper-background").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(wallpaper, app.settingsRepository.wallpaper.first())
            } finally {
                if (original == null) app.settingsRepository.clearWallpaper() else app.settingsRepository.saveWallpaper(original)
                app.wallpaperRepository.discard(wallpaper.imageId)
            }
        }
    }
}
