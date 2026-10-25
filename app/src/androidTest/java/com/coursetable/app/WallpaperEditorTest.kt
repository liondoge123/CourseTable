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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WallpaperConfig
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

class WallpaperEditorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var env: WallpaperTestEnvironment
    private val visible = mutableStateOf(true)
    private val mode = mutableStateOf(ThemeMode.LIGHT)
    private val accent = mutableStateOf(ThemeColor.BLUE)
    private val fontScale = mutableFloatStateOf(1f)
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
        compose.onNodeWithTag("wallpaper-pick").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper-slider-遮罩").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper-busy").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("wallpaper-position").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun adjust(label: String, value: Float) {
        compose.onNodeWithTag("wallpaper-slider-$label").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        val header = compose.onNodeWithTag("wallpaper-top-bar").getUnclippedBoundsInRoot()
        val slider = compose.onNodeWithTag("wallpaper-slider-$label").getUnclippedBoundsInRoot()
        assertTrue("Scrolled sliders must stay below the fixed header", slider.top >= header.bottom)
    }
    private fun capture(name: String) {
        compose.onRoot().captureToImage()
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
        compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { env.directory.listFiles()?.none { WallpaperConfig.validId(it.name) } == true }
        assertNull(runBlocking { env.settings.wallpaper.first() })
    }

    @Test fun applyResetAndRemoveCommitOnlyTheDisplayedAdjustments() {
        result = Uri.fromFile(env.source(1200, 1800))
        show()
        choose()
        adjust("缩放", 2f); adjust("模糊", 16f); adjust("遮罩", .65f); adjust("课表底板不透明度", 0f); adjust("课程卡片不透明度", 0f)
        compose.onNodeWithTag("wallpaper-reset").performScrollTo().performClick()
        compose.onNodeWithTag("wallpaper-slider-缩放").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 1f..3f))
        compose.onNodeWithTag("wallpaper-slider-课表底板不透明度").performScrollTo()
            .assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.65f, 0f..1f))
        compose.onNodeWithTag("wallpaper-slider-课程卡片不透明度").performScrollTo()
            .assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        adjust("遮罩", .5f)
        adjust("课表底板不透明度", .25f)
        adjust("课程卡片不透明度", .4f)
        compose.onNodeWithTag("wallpaper-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } != null }
        val saved = runBlocking { env.settings.wallpaper.first() }!!
        assertEquals(.5f, saved.mask); assertEquals(8f, saved.blurDp); assertEquals(1f, saved.scale)
        assertEquals(.25f, saved.gridOpacity, 0f)
        assertEquals(.4f, saved.cardOpacity, .0001f)
        assertTrue(java.io.File(env.directory, saved.imageId).isFile)
    }

    @Test fun removalRemainsDraftUntilAppliedAndMissingFilesCanBeRecovered() = runBlocking {
        val initial = env.repository.prepare(Uri.fromFile(env.source()))
        env.repository.apply(initial)
        java.io.File(env.directory, initial.imageId).delete()
        show(initial)
        compose.onNodeWithText("壁纸不可用，请更换图片").performScrollTo().assertExists()
        compose.onNodeWithTag("wallpaper-remove").performScrollTo().performClick()
        assertEquals(initial, env.settings.wallpaper.first())
        compose.onNodeWithTag("wallpaper-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { env.settings.wallpaper.first() } == null }
    }

    @Test fun previewSupportsFiveAccentsBothModesAndLargeText() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (color in ThemeColor.entries) {
                compose.runOnIdle { mode.value = theme; accent.value = color }
                compose.onNodeWithTag("wallpaper-preview").performScrollTo().assertIsDisplayed()
                capture("wallpaper-${theme.key}-${color.key}")
            }
        }
        compose.runOnIdle { fontScale.floatValue = 1.5f }
        compose.onNodeWithTag("wallpaper-apply").performScrollTo().assertIsDisplayed()
        capture("wallpaper-large-font")
        assertNull(runBlocking { env.settings.wallpaper.first() })
    }

    @Test fun draggingAndPinchingThePreviewPersistTheCrop() {
        result = Uri.fromFile(env.patternedSource())
        show()
        choose()
        compose.onNodeWithTag("wallpaper-position").performScrollTo().performTouchInput {
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
        compose.onNodeWithTag("wallpaper-apply").performScrollTo().performClick()
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
        compose.onNodeWithTag("wallpaper-pick").performScrollTo().performClick()
        assertEquals(initial, env.settings.wallpaper.first())
        result = Uri.fromFile(java.io.File(env.root, "broken.png").apply { writeText("invalid") })
        compose.onNodeWithTag("wallpaper-pick").performScrollTo().performClick()
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
