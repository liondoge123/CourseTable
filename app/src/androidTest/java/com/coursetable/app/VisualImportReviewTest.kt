package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import com.coursetable.app.data.Timetable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import android.graphics.Color
import androidx.core.content.FileProvider
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VisualImportReviewTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun contentUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.testfiles", file)
    private fun fixture(): VisualImportSession {
        val directory = File(context.cacheDir, "review-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "page.png")
        Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { bmp -> bmp.eraseColor(Color.WHITE); file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }
        return VisualImportSession(directory, listOf(ImportPage(0, file, 600, 400, emptyList()), ImportPage(1, file, 600, 400, emptyList())), PdfParseResult(emptyList(), emptyList(), listOf(ImportRegion("r", 0, .1f, .1f, .9f, .9f, TimetableLayout.UNKNOWN), ImportRegion("s", 1, .1f, .1f, .9f, .9f, TimetableLayout.UNKNOWN))), "test")
    }
    private fun course(name: String, review: Boolean = false) = CandidateCourse(name, dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0, sourceRegion = "r", needsReview = review)

    @Test fun glassProgressDialogIgnoresOutsideTap() {
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    GlassProgressDialog(
                        title = "正在识别课表…",
                        message = "完成后自动显示预览",
                        modifier = Modifier.testTag("image-recognition-progress")
                    )
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("正在识别课表…").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("正在识别课表…").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(Offset(center.x, height - 1f)) }
        compose.onNodeWithText("正在识别课表…").assertIsDisplayed()
    }

    @Test fun listFilteringEditingAddingDeletionAndReselectWarning() {
        val session = fixture()
        var edit = -1; var adds = 0; var reselects = 0
        try {
            compose.setContent {
                var courses by remember { mutableStateOf(listOf(course("数学"), course("待核对物理", true))) }
                CourseTableTheme { VisualImportReview(session, AppSettings(), courses, { courses = it }, { edit = it }, { adds++ }, {}, {}, hasEdits = true, onReselect = { reselects++ }) }
            }
            compose.onNodeWithText("新增框").assertDoesNotExist()
            compose.onNodeWithTag("import-original").assertDoesNotExist()
            compose.onNodeWithText("建议确认 1").performClick()
            compose.onNodeWithText("数学").assertDoesNotExist()
            compose.onNodeWithText("待核对物理").performClick()
            compose.onNodeWithContentDescription("保存").performClick()
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("添加课程").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("course-editor-page", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("校对课程").assertDoesNotExist()
            compose.onNodeWithContentDescription("取消").performClick()
            compose.onNodeWithText("全部 2").performClick()
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("查看原图").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("overlay-glass-sheet").assertExists()
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("调整识别范围").performClick()
            compose.onNodeWithText("重新识别？").assertExists()
            compose.onNodeWithTag("overlay-glass-sheet").assertExists()
            compose.runOnIdle { assertEquals(0, reselects) }
            compose.onNodeWithText("放弃修改并继续").performClick()
            compose.runOnIdle { assertEquals(1, reselects) }
        } finally { session.close() }
    }

    @Test fun sourceFragmentsSwitchPagesAndOpenZoomableOriginal() {
        val session = fixture()
        try {
            compose.setContent { CourseTableTheme { ImportCourseSource(session, course("数学").copy(sourceRegions = setOf("s")), AppSettings()) } }
            compose.onNodeWithText("1/2").assertExists()
            compose.onNodeWithText("下一个来源").performClick()
            compose.onNodeWithText("2/2").assertExists()
            compose.onNodeWithText("完整原图").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(10000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("overlay-glass-sheet").assertExists()
            compose.onNodeWithText("下一页").performClick()
            compose.onAllNodesWithText("2/2").assertCountEquals(2)
            compose.onNodeWithTag("import-source-image", useUnmergedTree = true).performTouchInput {
                down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
                moveTo(0, center - Offset(60f, 0f)); moveTo(1, center + Offset(60f, 0f))
                up(0); up(1)
                swipe(center, center + Offset(20f, 20f))
            }
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("放大片段").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(10000) { compose.onAllNodesWithTag("import-fragment-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("overlay-glass-sheet").assertExists()
            compose.onNodeWithTag("import-fragment-image", useUnmergedTree = true).performTouchInput {
                down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
                moveTo(0, center - Offset(60f, 0f)); moveTo(1, center + Offset(60f, 0f))
                up(0); up(1)
                swipe(center, center + Offset(20f, 20f))
            }
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-fragment-image", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("原图对照").assertExists()
        } finally { session.close() }
    }

    @Test fun nestedImageViewerKeepsCourseEditorDraft() {
        val session = fixture()
        val candidate = course("数学").copy(sourceRegions = setOf("r"), draftId = "draft")
        try {
            compose.setContent {
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize()) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        VisualImportReview(session, AppSettings(), listOf(candidate), {}, {}, {}, {}, {})
                    }
                }
            }
            compose.onNodeWithText("数学").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("course-editor-page", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("新教师")
            compose.onNodeWithText("完整原图").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(10000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-source-image", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("course-editor-page", useUnmergedTree = true).assertExists()
            compose.onAllNodes(hasSetTextAction())[1].assertTextContains("新教师")
        } finally { session.close() }
    }

    @Test fun selectionHandlesMoveResetAndKeepNormalizedCoordinatesDuringZoom() {
        val session = fixture()
        val file = session.pages.first().image
        val image = PreparedImportImage(session.directory, file, file, 600, 400, 1)
        var selection = ImageSelection()
        var confirms = 0
        try {
            compose.setContent {
                var rect by remember { mutableStateOf(selection) }
                CourseTableTheme { ImportImageSelection(image, rect, { rect = it; selection = it }, { confirms++ }, {}, false, null) }
            }
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(10000) { compose.onAllNodesWithTag("import-selection-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("overlay-glass-sheet").assertExists()
            compose.onNodeWithTag("import-selection-image", useUnmergedTree = true).performTouchInput {
                // The landscape image is fitted by width with 24dp margins.
                val margin = 24f * context.resources.displayMetrics.density
                val top = center.y - (width - margin * 2) / 3f
                swipe(Offset(margin, top), Offset(margin + 40f, top + 40f))
            }
            compose.runOnIdle { assertTrue(selection.left > 0f); assertTrue(selection.top > 0f) }
            val beforeMove = selection
            compose.onNodeWithTag("import-selection-image", useUnmergedTree = true).performTouchInput { swipe(center, center - Offset(20f, 20f)) }
            compose.runOnIdle {
                assertTrue(selection.left < beforeMove.left)
                assertEquals(beforeMove.right - beforeMove.left, selection.right - selection.left, .0001f)
                assertEquals(beforeMove.bottom - beforeMove.top, selection.bottom - selection.top, .0001f)
            }
            val cropped = selection
            compose.onNodeWithTag("import-selection-image", useUnmergedTree = true).performTouchInput {
                down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
                moveTo(0, center - Offset(60f, 0f)); moveTo(1, center + Offset(60f, 0f))
                up(0); up(1)
            }
            compose.runOnIdle { assertEquals(cropped, selection) }
            compose.onNodeWithText("重置").performClick()
            compose.runOnIdle { assertEquals(ImageSelection(), selection) }
            compose.onNodeWithText("确认并识别").performClick()
            compose.runOnIdle { assertEquals(1, confirms) }
        } finally { session.close() }
    }

    @Test fun incomingImageAutomaticallyRecognizesAndAllowsEmptyResultRetry() {
        val fixture = fixture()
        var recognitions = 0
        val firstRecognition = CompletableDeferred<Unit>()
        val preparedDirectories = mutableListOf<File>()
        val subpageStates = mutableListOf<Boolean>()
        try {
            compose.setContent {
                CourseTableTheme {
                    ImportScreen(incoming = IncomingFile(contentUri(fixture.pages.first().image), "image/png"), initialEntry = ImportEntry.INCOMING,
                        onSubpageChanged = { subpageStates += it },
                        recognizeImage = { _, image, selection, _ ->
                            recognitions++
                            if (recognitions == 1) firstRecognition.await()
                            preparedDirectories.add(image.directory)
                            val result = if (recognitions == 1) PdfParseResult(emptyList(), emptyList()) else PdfParseResult(listOf(course("数学")), emptyList(), fixture.result.regions)
                            val directory = File(context.cacheDir, "mock-result-${java.util.UUID.randomUUID()}").apply { mkdirs() }
                            VisualImportSession(directory, fixture.pages, result, "test", image, selection)
                        })
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("正在识别课表…").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("处理中…").assertDoesNotExist()
            firstRecognition.complete(Unit)
            compose.waitUntil(10000) { compose.onAllNodesWithText("未能识别课表").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(1, recognitions) }
            compose.onNodeWithTag("import-selection-image").assertDoesNotExist()
            compose.onNodeWithText("重试").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("导入确认").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(2, recognitions) }
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("调整识别范围").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("import-selection-page", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("课程数据导入").assertDoesNotExist()
            compose.runOnIdle { assertTrue(subpageStates.last()) }
            compose.onAllNodesWithText("返回").onLast().performClick()
            compose.onNodeWithText("导入确认").assertExists()
            compose.onNodeWithText("返回").performClick()
            compose.waitUntil(10000) { preparedDirectories.all { !it.exists() } }
        } finally { firstRecognition.complete(Unit); fixture.close() }
    }
    @Test fun filePickerImageAlsoRecognizesAutomatically() {
        val fixture = fixture()
        var launches = 0
        var recognitions = 0
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launches++
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(contentUri(fixture.pages.first().image)))
                }
            }
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    CourseTableTheme {
                        ImportScreen(initialEntry = ImportEntry.FILE, recognizeImage = { _, _, _, _ -> recognitions++; error("test recognition failure") })
                    }
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("未能识别课表").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(1, launches); assertEquals(1, recognitions) }
            compose.onNodeWithTag("import-selection-image").assertDoesNotExist()
        } finally { fixture.close() }
    }

    @Test fun issueQueueDeletionUndoAndConfirmationKeepRecordIdentity() {
        val fixture = fixture()
        val file = fixture.pages.first().image
        val image = PreparedImportImage(fixture.directory, file, file, 600, 400, 1)
        val session = VisualImportSession(fixture.directory, fixture.pages, fixture.result, "test", image, ImageSelection())
        var current = listOf(course("数学", true).copy(draftId = "a"), course("物理", true).copy(draftId = "b", dayOfWeek = 7))
        try {
            compose.setContent {
                var records by remember { mutableStateOf(current) }
                CourseTableTheme { VisualImportReview(session, AppSettings(), records, { records = it; current = it }, {}, {}, {}, {}) }
            }
            compose.onNodeWithText("开始校对").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("保存").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("保存").performClick()
            compose.runOnIdle { assertEquals("b", current.single().draftId); assertFalse(current.single().needsReview) }
            compose.onNodeWithText("撤销").performClick()
            compose.runOnIdle { assertEquals(listOf("a", "b"), current.map { it.draftId }); assertTrue(current.first().needsReview) }
            compose.onNodeWithText("导入 2 条记录").assertIsEnabled()
            compose.onNodeWithText("查看待确认列表").assertDoesNotExist()
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("添加课程").assertExists()
            compose.onNodeWithText("查看原图").assertExists()
            compose.onNodeWithText("调整识别范围").assertDoesNotExist()
            compose.onNodeWithText("更多操作").assertDoesNotExist()
            compose.onRoot().performTouchInput { click(Offset(center.x, height - 1f)) }
            compose.onNodeWithText("添加课程").assertDoesNotExist()
            compose.onNodeWithText("开始校对").performClick()
            compose.onNodeWithContentDescription("保存").performClick()
            compose.onNodeWithText("导入 2 条记录").assertIsEnabled()
        } finally { session.close() }
    }

    @Test fun directImportSkipsRecognitionDoubtsButNeverInvalidFields() {
        val fixture = fixture()
        val file = fixture.pages.first().image
        val image = PreparedImportImage(fixture.directory, file, file, 600, 400, 1)
        val session = VisualImportSession(fixture.directory, fixture.pages, fixture.result, "test", image, ImageSelection())
        var records by mutableStateOf(listOf(course("数学", true).copy(draftId = "a")))
        var imports = 0
        try {
            compose.setContent { CourseTableTheme { VisualImportReview(session, AppSettings(), records, { records = it }, {}, {}, {}, { imports++ }) } }
            compose.onNodeWithText("导入 1 条记录").assertIsEnabled().performClick()
            compose.onAllNodesWithText("确认导入").onLast().performClick()
            compose.runOnIdle { assertEquals(1, imports); assertTrue(records.single().needsReview); records = records.map { it.copy(dayOfWeek = 0) } }
            compose.onNodeWithText("处理 1 条必修正项").performClick()
            compose.onNodeWithText("信息正确").assertDoesNotExist()
            compose.onNodeWithContentDescription("保存").assertIsEnabled()
        } finally { session.close() }
    }

    private fun screenshot(tag: String, name: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "import-validation").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun editingAndImportKeepSourcesAndWriteOnlyAfterConfirmation() {
        val app = context.applicationContext as CourseApp
        val originalSettings = runBlocking { app.settingsRepository.settings.first() }
        val timetableId = runBlocking {
            val id = app.database.timetableDao().upsert(Timetable(name = "导入验证"))
            app.settingsRepository.setActiveTimetable(id)
            id
        }
        val directory = File(context.cacheDir, "sample-import-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "source.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets.open("selection-details.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
        val sample = BitmapFactory.decodeFile(file.absolutePath)
        val width = sample.width; val height = sample.height; sample.recycle()
        try {
            compose.setContent {
                CourseTableTheme {
                    ImportScreen(incoming = IncomingFile(contentUri(file), "image/jpeg"), recognizeImage = { _, image, selection, _ ->
                        val resultDirectory = File(context.cacheDir, "integration-result-${java.util.UUID.randomUUID()}").apply { mkdirs() }
                        val output = File(resultDirectory, "page.jpg"); file.copyTo(output)
                        val region = ImportRegion("r", 0, 0f, .08f, 1f, .16f, TimetableLayout.UNKNOWN)
                        val record = course("自动控制原理B", true).copy(location = "X1412", teacher = "赵舵")
                        VisualImportSession(resultDirectory, listOf(ImportPage(0, output, width, height, emptyList())), PdfParseResult(listOf(record), emptyList(), listOf(region)), "test", image, selection)
                    })
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("import-review-page").fetchSemanticsNodes().isNotEmpty() }
            screenshot("import-review-page", "review.png")
            compose.onNodeWithText("开始校对").performClick()
            compose.onNodeWithText("原图对照").assertExists()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(3)
            compose.onNodeWithText("效果预览").assertDoesNotExist()
            compose.onNodeWithText("修改").assertDoesNotExist()
            compose.onNodeWithContentDescription("保存").performClick()
            compose.onNodeWithText("导入 1 条记录").assertIsEnabled()
            compose.runOnIdle { assertTrue(runBlocking { app.database.courseDao().byTimetableOnce(timetableId) }.isEmpty()) }
            compose.onNodeWithText("自动控制原理B").performClick()
            compose.onNodeWithText("此记录没有对应的原图片段，可查看完整原图").assertDoesNotExist()
            compose.onNodeWithText("完整原图").assertExists()
            compose.onNodeWithContentDescription("取消").performClick()
            compose.onNodeWithText("更多").performClick()
            compose.onNodeWithText("添加课程").performClick()
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("补充课程")
            compose.onNodeWithContentDescription("保存").performClick()
            compose.runOnIdle { assertTrue(runBlocking { app.database.courseDao().byTimetableOnce(timetableId) }.isEmpty()) }
            compose.onNodeWithText("导入 2 条记录").performClick()
            compose.onAllNodesWithText("确认导入").onLast().performClick()
            compose.waitUntil(10000) { runBlocking { app.database.courseDao().byTimetableOnce(timetableId) }.size == 2 }
            val imported = runBlocking { app.database.courseDao().byTimetableOnce(timetableId) }
            assertEquals(setOf("自动控制原理B", "补充课程"), imported.map { it.name }.toSet())
        } finally {
            runBlocking {
                app.settingsRepository.setActiveTimetable(originalSettings.timetableId)
                app.database.courseDao().deleteByTimetable(timetableId)
                app.database.courseDao().deleteTimetable(timetableId)
            }
            directory.deleteRecursively()
        }
    }

}
