package com.coursetable.app

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.VisualImportReview
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class PreviewScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultEditorRetainsPreviewAndColorPicker() {
        val course = com.coursetable.app.data.Course(name = "数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    CourseEditorDialog(course, 18, 12, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("效果预览").assertIsDisplayed()
        compose.onNodeWithTag("course-editor-pinned-source", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("课程颜色", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun circularProgressIndicatorRotates() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CourseTableTheme {
                Box(Modifier.size(64.dp).background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp).testTag("animated-progress"), color = Color.Blue, strokeWidth = 4.dp)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val first = compose.onNodeWithTag("animated-progress").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(225)
        val second = compose.onNodeWithTag("animated-progress").captureToImage().asAndroidBitmap()
        assertFalse(first.sameAs(second))
        compose.mainClock.autoAdvance = true
    }

    @Test fun editorTextFieldsHaveVisibleContainers() {
        val course = com.coursetable.app.data.Course(name = "", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
        compose.setContent {
            CourseTableTheme(themeMode = ThemeMode.LIGHT) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    CourseEditorDialog(course, 18, 12, {}, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        val field = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].captureToImage().asAndroidBitmap()
        val outsideCorner = field.getPixel(0, 0)
        val fieldFill = field.getPixel(field.width / 2, field.height - 6)
        assertNotEquals(outsideCorner, fieldFill)
    }

    @Test fun captureCurrentPreviewWithSampleRecords(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "preview-screenshot-source.jpg")
        instrumentation.context.assets.open("selection-details.jpg").use { input -> source.outputStream().use { input.copyTo(it) } }
        val image = ImageImportPreparation.prepare(context, Uri.fromFile(source))
        val fixture = instrumentation.context.assets.open("selection-details.expected.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val expected = fixture.getJSONArray("names")
        val sample = BitmapFactory.decodeFile(source.absolutePath)
        val sampleWidth = sample.width; val sampleHeight = sample.height; sample.recycle()
        val schedules = listOf(
            listOf(0,1,1,2,1,17), listOf(1,2,3,2,1,17), listOf(2,3,3,2,1,17),
            listOf(3,4,3,3,1,17), listOf(4,2,9,2,1,17), listOf(5,2,6,2,1,17),
            listOf(5,4,1,2,1,17), listOf(6,5,6,2,2,17), listOf(7,5,3,3,1,17),
            listOf(8,3,6,2,1,17), listOf(9,4,9,2,4,6), listOf(10,3,11,2,4,4),
            listOf(10,3,11,2,9,10), listOf(10,3,11,2,14,16)
        )
        val courses = schedules.mapIndexed { i, row -> CandidateCourse(expected.getString(row[0]), teacher = if (row[0] == 0) "赵舵" else "", location = "X1412(犀浦)", dayOfWeek = row[1], startSection = row[2], duration = row[3], startWeek = row[4], endWeek = row[5], weekType = 0, needsReview = i % 2 == 0, sourceRegion = "row-${row[0]}") }
        val directory = File(context.cacheDir, "preview-screenshot-session").apply { mkdirs() }
        val edges = fixture.getJSONArray("rowEdges")
        val regions = (0 until expected.length()).map { row -> ImportRegion("row-$row", 0, 0f, edges.getInt(row).toFloat() / sampleHeight, 1f, edges.getInt(row + 1).toFloat() / sampleHeight, TimetableLayout.UNKNOWN) }
        val session = VisualImportSession(directory, listOf(ImportPage(0, source, sampleWidth, sampleHeight, emptyList())), PdfParseResult(courses, emptyList(), regions), "sample", image, ImageSelection())
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var keyboard: SoftwareKeyboardController? = null
        var activityWindow: android.view.Window? = null
        var imeVisible = false
        val output = File(context.getExternalFilesDir(null), "import-validation").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            // Flush the app frame before the full display capture includes system bars and IME.
            compose.onNodeWithTag("screenshot-scene", useUnmergedTree = true).captureToImage()
            Thread.sleep(250)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(output, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
        try {
            compose.setContent {
                val activity = LocalActivity.current as ComponentActivity
                val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { activityWindow = activity.window; imeVisible = imeBottom > 0 }
                DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
                keyboard = LocalSoftwareKeyboardController.current
                var records by remember { mutableStateOf(session.result.candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }) }
                CourseTableTheme(themeMode = mode) {
                    LiquidBackdropHost(Modifier.fillMaxSize().testTag("screenshot-scene")) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            VisualImportReview(session, AppSettings(), records, { records = it }, {}, {}, {}, {})
                        }
                    }
                }
            }
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                compose.runOnIdle { mode = theme }
                compose.waitUntil(5000) { activityWindow?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars == (theme == ThemeMode.LIGHT) } == true }
                compose.mainClock.advanceTimeBy(400)
                capture("preview-${theme.key}.png")
                compose.onNodeWithText("开始校对").performClick()
                compose.mainClock.advanceTimeBy(400)
                compose.waitForIdle()
                compose.onNodeWithText("课程颜色").assertDoesNotExist()
                compose.onNodeWithText("效果预览").assertDoesNotExist()
                capture("editor-${theme.key}.png")
                compose.onNodeWithTag("course-editor-pinned-source", useUnmergedTree = true).assertIsDisplayed()
                if (theme == ThemeMode.LIGHT) {
                    compose.onNodeWithText("完整原图").performClick()
                    compose.waitUntil(10000) { compose.onAllNodesWithTag("import-source-image").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("import-source-image").assertIsDisplayed()
                    compose.onNodeWithText("关闭").performClick()
                    compose.onNodeWithContentDescription("对应原图片段，点击放大").performClick()
                    compose.onNodeWithText("双指缩放，拖动查看").assertExists()
                    compose.onNodeWithText("关闭").performClick()
                    compose.onNodeWithText("第 1 节").performScrollTo().performClick()
                    compose.onNodeWithText("选择开始节次").assertExists()
                    compose.onNodeWithText("确定").performClick()
                }
                val before = compose.onNodeWithTag("course-editor-pinned-source", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                compose.onNodeWithTag("course-editor-form", useUnmergedTree = true).performTouchInput { swipeUp() }
                val after = compose.onNodeWithTag("course-editor-pinned-source", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(before, after)
                compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performScrollTo().performClick()
                compose.runOnIdle { keyboard?.show() }
                compose.waitUntil(5000) { imeVisible }
                capture("editor-keyboard-${theme.key}.png")
                compose.onNodeWithTag("course-editor-pinned-source", useUnmergedTree = true).assertIsDisplayed()
                compose.runOnIdle { keyboard?.hide() }
                compose.waitUntil(5000) { !imeVisible }
                compose.onNodeWithContentDescription("取消").performClick()
            }
        } finally { session.close(); image.close(); source.delete() }
    }
}
