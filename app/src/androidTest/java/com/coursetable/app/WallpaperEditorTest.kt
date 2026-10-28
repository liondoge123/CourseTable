package com.coursetable.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.WallpaperEditorScreen
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class WallpaperEditorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var env: WallpaperTestEnvironment
    private val visible = mutableStateOf(true)
    private val mode = mutableStateOf(ThemeMode.LIGHT)
    private val accent = mutableStateOf(ThemeColor.BLUE)
    private val fontScale = mutableFloatStateOf(1f)
    private var touchSlop = 0f
    private var result: Uri? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, Intent().setData(result))
        }
    }
    @Before fun setup() { env = WallpaperTestEnvironment() }
    @After fun cleanup() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        // The editor's cancellation cleanup is scheduled in the application's scope.
        runBlocking { env.repository.initialize() }
        env.close()
    }
    private fun show(initial: WallpaperConfig? = null) {
        compose.setContent {
            val density = LocalDensity.current
            touchSlop = LocalViewConfiguration.current.touchSlop
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }, LocalDensity provides Density(density.density, fontScale.floatValue)) {
                CourseTableTheme(mode.value, accent.value) {
                    LiquidBackdropHost(Modifier.fillMaxSize()) {
                        if (visible.value) WallpaperEditorScreen(AppSettings(wallpaper = initial), listOf(
                            Course(name = "高等数学", location = "教学楼 302", teacher = "张老师", dayOfWeek = 1,
                                startSection = 1, duration = 2, startWeek = 1, endWeek = 30),
                            Course(name = "大学英语", location = "综合楼 201", dayOfWeek = 3,
                                startSection = 2, duration = 2, startWeek = 1, endWeek = 30, color = 0xFF30D158)
                        ), onBack = { visible.value = false }, repository = env.repository)
                    }
                }
            }
        }
    }
    private fun choose() {
        compose.onNodeWithTag("wallpaper-pick").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper-busy").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper-position").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openAdjustments() {
        if (compose.onAllNodesWithTag("wallpaper-adjustments").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("wallpaper-adjust").performClick()
            compose.mainClock.advanceTimeBy(350)
        }
        compose.onNodeWithTag("wallpaper-adjustments").assertIsDisplayed()
    }
    private fun closeAdjustments() {
        compose.onNodeWithTag("wallpaper-adjustments-done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("wallpaper-adjustments").fetchSemanticsNodes().isEmpty() }
    }
    private fun adjust(label: String, value: Float) {
        openAdjustments()
        compose.onNodeWithTag("wallpaper-slider-$label")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.onNodeWithTag("wallpaper-slider-$label").assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        captureWallpaperScreenshot(name)
    }

    @Test fun choosingAndCancellingNeverPublishesTheDraft() {
        result = Uri.fromFile(env.source())
        show()
        choose()
        adjust("遮罩", .65f)
        adjust("课表底板不透明度", .25f)
        adjust("课程卡片不透明度", .4f)
        assertNull(runBlocking { env.settings.wallpaper.first() })
        compose.onNodeWithText("取消").assertDoesNotExist()
        closeAdjustments()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { env.directory.listFiles()?.none { WallpaperConfig.validId(it.name) } == true }
        assertNull(runBlocking { env.settings.wallpaper.first() })
    }

    @Test fun applyResetAndRemoveCommitOnlyTheDisplayedAdjustments() {
        result = Uri.fromFile(env.source(1200, 1800))
        show()
        choose()
        adjust("缩放", 2f); adjust("模糊", 16f); adjust("遮罩", .65f); adjust("课表底板不透明度", 0f); adjust("课程卡片不透明度", 0f)
        compose.onNodeWithTag("wallpaper-reset").performClick()
        compose.onNodeWithTag("wallpaper-slider-缩放").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 1f..3f))
        compose.onNodeWithTag("wallpaper-slider-课表底板不透明度")
            .assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.65f, 0f..1f))
        compose.onNodeWithTag("wallpaper-slider-课程卡片不透明度")
            .assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        adjust("遮罩", .5f)
        adjust("课表底板不透明度", .25f)
        adjust("课程卡片不透明度", .4f)
        closeAdjustments()
        compose.onNodeWithTag("wallpaper-apply").performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } != null }
        val saved = runBlocking { env.settings.wallpaper.first() }!!
        assertEquals(.5f, saved.mask); assertEquals(8f, saved.blurDp); assertEquals(1f, saved.scale)
        assertEquals(.25f, saved.gridOpacity, 0f)
        assertEquals(.4f, saved.cardOpacity, .0001f)
        assertTrue(java.io.File(env.directory, saved.imageId).isFile)
    }

    @Test fun textChoicesPreviewWithoutSavingAndPersistOnlyWhenApplied() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        openAdjustments()
        compose.onNodeWithTag("wallpaper-text-tab").performClick()
        compose.onNodeWithTag("wallpaper-text-clarity").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("settings-choice-wallpaper-text-mode").performClick()
        compose.onNodeWithTag("wallpaper-text-mode-light").performClick()
        compose.onNodeWithTag("settings-choice-wallpaper-text-mode").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "浅色"))
        assertNull(runBlocking { env.settings.wallpaper.first() })
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureToImage()
        capture("wallpaper-text-controls")
        closeAdjustments()
        compose.onNodeWithTag("wallpaper-apply").performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } != null }
        val saved = runBlocking { env.settings.wallpaper.first() }!!
        assertEquals(WallpaperTextMode.LIGHT, saved.textMode)
        assertFalse(saved.textClarity)
    }

    @Test fun previewFillsTheEditorAndAdjustmentsAreInANonScrollingSheet() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        val editor = compose.onNodeWithTag("wallpaper-editor")
        assertFalse(editor.fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange))
        assertEquals(editor.getUnclippedBoundsInRoot(),
            compose.onNodeWithTag("wallpaper-preview").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("wallpaper-adjustments").assertDoesNotExist()
        capture("wallpaper-fullscreen-preview")
        openAdjustments()
        val panel = compose.onNodeWithTag("wallpaper-adjustments")
        assertFalse(panel.fetchSemanticsNode().config.contains(SemanticsProperties.VerticalScrollAxisRange))
        for (label in listOf("缩放", "模糊", "遮罩", "课表底板不透明度", "课程卡片不透明度")) {
            compose.onNodeWithTag("wallpaper-slider-$label").assertIsDisplayed()
        }
        compose.onNodeWithTag("wallpaper-adjustments-done").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-reset").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-remove").assertIsDisplayed()
        capture("wallpaper-adjustment-sheet")
        closeAdjustments()
        compose.onNodeWithTag("wallpaper-position").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-apply").assertIsDisplayed()
        assertNull(runBlocking { env.settings.wallpaper.first() })
    }
    @Test fun slidersIgnoreTrackTapsButStillSupportHorizontalDragging() {
        result = Uri.fromFile(env.source())
        show()
        choose()
        adjust("遮罩", .4f)
        val slider = compose.onNodeWithTag("wallpaper-slider-遮罩")
        slider.performTouchInput { click(Offset(width * .25f, centerY)) }
        val tapped = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals("Touching the track must not jump the value", .4f, tapped, 0f)
        val before = compose.onNodeWithTag("wallpaper-adjustments").getUnclippedBoundsInRoot()
        slider.performTouchInput {
            swipe(Offset(width * .5f, centerY), Offset(width * .75f, centerY + 6f), 500)
        }
        val dragged = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("Horizontal dragging must adjust the value", dragged in 0.5f..0.7f)
        assertEquals("Horizontal dragging must keep the panel still", before,
            compose.onNodeWithTag("wallpaper-adjustments").getUnclippedBoundsInRoot())
    }

    @Test fun draggingRevealsTheActualPreviewAndRestoresTheSheetOnReleaseOrCancellation() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        for (cancelGesture in listOf(false, true)) {
            adjust("遮罩", .2f)
            val slider = compose.onNodeWithTag("wallpaper-slider-遮罩")
            slider.performTouchInput {
                down(Offset(width * .25f, centerY))
                moveTo(Offset(width * .55f, centerY), 300)
            }
            var activeBounds = androidx.compose.ui.geometry.Rect.Zero
            var rootBounds = androidx.compose.ui.geometry.Rect.Zero
            var thumbInRoot = Offset.Zero
            val during = try {
                // Continue the same gesture after the panel becomes transparent.
                slider.performTouchInput { moveTo(Offset(width * .7f, centerY), 200) }
                val continued = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
                assertTrue("The active drag must continue after the panel fades", continued in 0.5f..0.7f)
                slider.assertIsDisplayed()
                compose.onNodeWithText("遮罩").assertIsDisplayed()
                compose.onNodeWithText("${(continued * 100).roundToInt()}%").assertIsDisplayed()
                // Transparent controls remain laid out; verify their actual appearance in pixels.
                compose.onNodeWithTag("wallpaper-slider-模糊").assertExists()
                compose.onNodeWithTag("wallpaper-adjustments-done").assertExists()
                activeBounds = compose.onNodeWithTag("wallpaper-adjustment-遮罩")
                    .fetchSemanticsNode().boundsInRoot
                rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val sliderBounds = slider.fetchSemanticsNode().boundsInRoot
                thumbInRoot = Offset(sliderBounds.left + sliderBounds.width * .7f, sliderBounds.center.y)
                capture("wallpaper-dragging-preview")
                compose.onRoot().captureToImage().toPixelMap()
            } finally {
                slider.performTouchInput { if (cancelGesture) cancel() else up() }
            }
            slider.assertIsDisplayed()
            compose.onNodeWithTag("wallpaper-adjustments-done").assertIsDisplayed()
            val restored = compose.onRoot().captureToImage().toPixelMap()
            capture("wallpaper-dragging-restored")
            closeAdjustments()
            val preview = compose.onRoot().captureToImage().toPixelMap()
            var restoredDifference = 0f
            for (xFraction in listOf(.15f, .5f, .85f)) {
                for (yFraction in listOf(.2f, .4f, .5f, .6f, .7f, .8f)) {
                    val x = (preview.width * xFraction).toInt()
                    val y = (preview.height * yFraction).toInt()
                    // The active label, value and slider intentionally remain over the preview.
                    if (activeBounds.contains(Offset(x + rootBounds.left, y + rootBounds.top))) continue
                    val expected = preview[x, y]
                    val actual = during[x, y]
                    val difference = kotlin.math.abs(expected.red - actual.red) +
                        kotlin.math.abs(expected.green - actual.green) + kotlin.math.abs(expected.blue - actual.blue)
                    assertTrue("Dragging must expose the actual preview without sheet or scrim at $x,$y", difference < .03f)
                    val sheetDifference = kotlin.math.abs(restored[x, y].red - actual.red) +
                        kotlin.math.abs(restored[x, y].green - actual.green) + kotlin.math.abs(restored[x, y].blue - actual.blue)
                    if (yFraction < .3f) {
                        assertTrue("Opening the wallpaper sheet must not dim the visible preview", sheetDifference < .03f)
                    }
                    restoredDifference += sheetDifference
                }
            }
            val thumbX = (thumbInRoot.x - rootBounds.left).toInt()
            val thumbY = (thumbInRoot.y - rootBounds.top).toInt()
            fun thumbDifference(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) =
                kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
            assertTrue("The active thumb must remain visible while the sheet surface is hidden",
                thumbDifference(during[thumbX, thumbY], restored[thumbX, thumbY]) < .03f)
            assertTrue("The active thumb must be drawn above the preview",
                thumbDifference(during[thumbX, thumbY], preview[thumbX, thumbY]) > .2f)
            assertTrue("The sheet must return when the gesture ends", restoredDifference > .3f)
        }
    }

    @Test fun verticalGesturesOnSlidersWithInitialSidewaysWobbleDoNotAdjustThem() {
        result = Uri.fromFile(env.source())
        show()
        choose()
        val ranges = linkedMapOf("缩放" to 1f..3f, "模糊" to 0f..24f, "遮罩" to 0f..0.8f,
            "课表底板不透明度" to 0f..1f, "课程卡片不透明度" to 0f..1f)
        ranges.forEach { (label, range) -> adjust(label, (range.start + range.endInclusive) / 2) }
        for ((label, range) in ranges) {
            val slider = compose.onNodeWithTag("wallpaper-slider-$label")
            for (startFraction in listOf(.5f, .75f)) {
                val before = compose.onNodeWithTag("wallpaper-adjustments").getUnclippedBoundsInRoot()
                val direction = -1f
                slider.performTouchInput {
                    val start = Offset(width * startFraction, centerY)
                    down(start)
                    advanceEventTime(80)
                    // Exceed the old horizontal threshold before the intended vertical movement.
                    moveTo(start + Offset(touchSlop * 1.25f, 2f * direction), 16)
                    moveTo(start + Offset(touchSlop * 1.25f, 140f * direction), 300)
                    up()
                }
                slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    (range.start + range.endInclusive) / 2, range))
                assertEquals("Vertical finger movement must leave the panel in place", before,
                    compose.onNodeWithTag("wallpaper-adjustments").getUnclippedBoundsInRoot())
            }
            // A short scroll can end before the parent's touch slop: never treat it as a seek tap.
            slider.performTouchInput {
                val start = Offset(width * .75f, centerY)
                swipe(start, start + Offset(0f, touchSlop * .5f), 200)
            }
            slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(
                (range.start + range.endInclusive) / 2, range))
        }
    }

    @Test fun removalRemainsDraftUntilAppliedAndMissingFilesCanBeRecovered() = runBlocking {
        val initial = env.repository.prepare(Uri.fromFile(env.source()))
        env.repository.apply(initial)
        java.io.File(env.directory, initial.imageId).delete()
        show(initial)
        compose.onNodeWithText("壁纸不可用，请更换图片").assertExists()
        openAdjustments()
        compose.onNodeWithTag("wallpaper-remove").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("wallpaper-adjustments").fetchSemanticsNodes().isEmpty() }
        assertEquals(initial, env.settings.wallpaper.first())
        compose.onNodeWithTag("wallpaper-apply").performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } == null }
    }

    @Test fun previewSupportsFiveAccentsBothModesAndLargeText() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (color in ThemeColor.entries) {
                compose.runOnIdle { mode.value = theme; accent.value = color }
                compose.onNodeWithTag("wallpaper-preview").assertIsDisplayed()
                capture("wallpaper-${theme.key}-${color.key}")
            }
        }
        compose.runOnIdle { fontScale.floatValue = 1.5f }
        compose.onNodeWithTag("wallpaper-apply").assertIsDisplayed()
        openAdjustments()
        compose.onNodeWithTag("wallpaper-slider-缩放").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-slider-课程卡片不透明度").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-reset").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-remove").assertIsDisplayed()
        capture("wallpaper-large-font")
        assertNull(runBlocking { env.settings.wallpaper.first() })
    }

    @Test fun draggingAndPinchingThePreviewPersistTheCrop() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        compose.onNodeWithTag("wallpaper-position").performTouchInput {
            down(0, center + Offset(-35f, 0f)); down(1, center + Offset(35f, 0f))
            for (i in 1..8) {
                moveTo(0, center + Offset(-35f - i * 9f, 0f))
                moveTo(1, center + Offset(35f + i * 9f, 0f))
            }
            up(0); up(1)
        }
        compose.onNodeWithTag("wallpaper-position").performTouchInput {
            swipe(center, center + Offset(width * .2f, height * .1f), 300)
        }
        compose.onNodeWithTag("wallpaper-apply").performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } != null }
        val saved = runBlocking { env.settings.wallpaper.first() }!!
        assertTrue("Pinching must change zoom", saved.scale > 1.1f)
        assertTrue("Dragging must change the image position", saved.positionX < .5f || saved.positionY < .5f)
    }

    @Test fun cancelledOrBrokenSelectionKeepsTheCurrentWallpaper() = runBlocking {
        val initial = env.repository.prepare(Uri.fromFile(env.source()))
        env.repository.apply(initial)
        show(initial)
        result = null
        compose.onNodeWithTag("wallpaper-pick").performClick()
        assertEquals(initial, env.settings.wallpaper.first())
        result = Uri.fromFile(java.io.File(env.root, "broken.png").apply { writeText("invalid") })
        compose.onNodeWithTag("wallpaper-pick").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("无法识别这张图片").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(initial, env.settings.wallpaper.first())
        assertEquals(setOf(initial.imageId), env.directory.list()!!.toSet())
    }
}

internal fun captureWallpaperScreenshot(name: String) {
    val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
    require(directory.matches(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$")))
    require(name.matches(Regex("^wallpaper-[a-z-]+$")))
    ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("screencap -p $directory/$name.png")).use { it.readBytes() }
}
