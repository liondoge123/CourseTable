package com.coursetable.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class UnifiedImportReviewTest {
    @get:Rule val compose = createComposeRule()
    private fun sample() = listOf(
        CandidateCourse("高等数学", teacher = "陈老师", location = "教学楼 A201", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, draftId = "math"),
        CandidateCourse("大学英语", location = "B302", dayOfWeek = 3, startSection = 3, duration = 2, startWeek = 1, endWeek = 16, weekType = 1, needsReview = true, draftId = "english"),
        CandidateCourse("物理实验", dayOfWeek = 5, startSection = 5, duration = 5, startWeek = 2, endWeek = 16, weekType = 2, draftId = "physics")
    )
    private fun show(initial: List<CandidateCourse>, dark: Boolean = false, confirm: (Boolean) -> Unit = {}) {
        compose.setContent {
            var records by remember { mutableStateOf(initial) }
            CourseTableTheme(themeMode = if(dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    UnifiedImportReview(null, AppSettings(), records, "Excel/CSV 表格", emptyList(), { records = it }, {}, confirm, false, true, null)
                }
            }
        }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "review-$name.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    @Test fun previewListAndAdvisoryConfirmation() {
        var imported = false
        show(sample(), confirm = { imported = true })
        compose.onNodeWithText("导入确认").assertIsDisplayed()
        screenshot("light-preview")
        compose.onNodeWithText("建议确认 1", useUnmergedTree = true).performClick()
        compose.onNodeWithText("大学英语").assertIsDisplayed()
        compose.onNodeWithText("高等数学").assertDoesNotExist()
        screenshot("light-list")
        compose.onNodeWithText("导入 3 条记录").performClick()
        compose.onNodeWithText("返回校对").assertIsDisplayed()
        screenshot("light-confirm")
        compose.onAllNodesWithText("确认导入").onLast().performClick()
        compose.runOnIdle { assertTrue(imported) }
    }
    @Test fun errorsLeadToEditorAndSavedRecordLeavesQueue() {
        show(sample().map { if(it.draftId == "math") it.copy(name = "") else it })
        compose.onNodeWithText("处理 1 条必修正项").performClick()
        compose.onNodeWithText("课程名称 *").performTextInput("高等数学")
        compose.onNodeWithText("保存并校对下一条").performClick()
        compose.onNodeWithText("大学英语").assertExists()
        screenshot("light-editor")
        compose.onNodeWithText("保存并校对下一条").performClick()
        compose.onNodeWithText("✓ 校对完成").assertExists()
        compose.onNodeWithText("导入 3 条记录").assertIsEnabled()
    }
    @Test fun darkPreviewAndOverwriteConfirmation() {
        show(sample(), dark = true)
        screenshot("dark-preview")
        compose.onNodeWithText("追加 ▾", substring = true).performClick()
        compose.onNodeWithText("导入 3 条记录").performClick()
        compose.onNodeWithText("覆盖并导入").assertIsDisplayed()
        screenshot("dark-confirm")
    }

    @Test fun previewUsesGroupedReviewListPresentation() {
        show(sample())
        compose.onNodeWithText("周一").assertIsDisplayed()
        compose.onNodeWithText("周三").assertExists()
        compose.onNodeWithText("课表").assertDoesNotExist()
        compose.onNodeWithText("清单").assertDoesNotExist()
        compose.onNodeWithText("全部周次", substring = true).assertDoesNotExist()
        compose.onNodeWithText("08:00").assertDoesNotExist()
        compose.onNodeWithText("08:45").assertDoesNotExist()
        compose.onNodeWithText("周一 · 第 1—2 节 · 1—16 周").assertExists()
        val destination = compose.onNodeWithText("追加 ▾", substring = true).fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithText("导入 3 条记录").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(destination.center.y - action.center.y) < 2f)
        compose.onNodeWithTag("review-summary-bar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("review-import-bar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun previewDisplaysEveryOverlappingCourse() {
        show(
            listOf(
                CandidateCourse("课程甲", dayOfWeek = 1, startSection = 1, duration = 4, startWeek = 1, endWeek = 16, weekType = 0, draftId = "a"),
                CandidateCourse("课程乙", dayOfWeek = 1, startSection = 2, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, draftId = "b"),
                CandidateCourse("课程丙", dayOfWeek = 1, startSection = 3, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, draftId = "c")
            )
        )

        compose.onNodeWithText("课程甲").assertExists()
        compose.onNodeWithText("课程乙").assertExists()
        compose.onNodeWithText("课程丙").assertExists()
    }

    @Test fun editedPanelKeepsUnsavedChangesUntilExplicitlyDiscarded() {
        show(sample())
        compose.onNodeWithText("开始校对").performClick()
        compose.onNodeWithText("课程名称 *").performTextReplacement("修改后的英语")
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithText("保存校对修改？").assertIsDisplayed()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("开始校对").performClick()
        compose.onNodeWithText("大学英语").assertExists()
    }

    @Test fun longCourseKeepsFiveSectionsAfterReview() {
        show(listOf(sample().last().copy(needsReview = true)))
        compose.onNodeWithText("开始校对").performClick()
        compose.onNodeWithText("共 5 节").performScrollTo().assertIsDisplayed()
        screenshot("long-course-editor")
        compose.onNodeWithText("保存并校对下一条").performClick()
        compose.onNodeWithText("周五 · 第 5—9 节 · 2—16 周 · 双周").assertExists()
    }

    @Test fun csvUsesUnifiedPreviewAndFailedImportRetainsDraftForRetry() = importFile(false)
    @Test fun icsUsesUnifiedPreviewAndAtomicConfirmation() = importFile(true)

    @Test fun captureNormalTimetableForDesignComparison() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val original = runBlocking { app.settingsRepository.settings.first() }
        val id = runBlocking {
            val id = app.database.timetableDao().upsert(com.coursetable.app.data.Timetable(name = "视觉对照", semesterStartEpochDay = java.time.LocalDate.now().toEpochDay()))
            app.settingsRepository.setActiveTimetable(id)
            app.courseRepository.importCourses(id, sample().map { candidateToCourse(it, id) }, false)
            id
        }
        try {
            compose.setContent {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel { TimetableViewModel(app) }
                CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    TimetableScreen(vm)
                } }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText("高等数学", substring = true).fetchSemanticsNodes().isNotEmpty() }
            screenshot("normal-timetable")
        } finally {
            runBlocking {
                app.settingsRepository.setActiveTimetable(original.timetableId)
                app.database.courseDao().deleteByTimetable(id)
                app.database.courseDao().deleteTimetable(id)
            }
        }
    }

    private fun importFile(ics: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CourseApp
        val original = runBlocking { app.settingsRepository.settings.first() }
        val id = runBlocking {
            val id = app.database.timetableDao().upsert(com.coursetable.app.data.Timetable(name = "统一导入测试"))
            app.settingsRepository.setActiveTimetable(id)
            id
        }
        val source = File(context.cacheDir, "unified-test.${if(ics) "ics" else "csv"}")
        source.writeText(if(ics) "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:review\nSUMMARY:导入测试\nDTSTART:20260902T080000\nDTEND:20260902T094000\nEND:VEVENT\nEND:VCALENDAR" else "课程名称,星期,开始节数,结束节数,老师,地点,周数\n导入测试,1,1,2,教师,A201,1-16")
        try {
            compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
                ImportScreen(incoming = IncomingFile(Uri.fromFile(source), if(ics) "text/calendar" else "text/csv"))
            } } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("导入确认").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("导入测试").assertExists()
            assertTrue(runBlocking { app.courseRepository.byTimetableOnce(id) }.isEmpty())
            if(!ics) app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_review BEFORE INSERT ON courses WHEN NEW.name = '导入测试' BEGIN SELECT RAISE(ABORT, 'retry test'); END")
            compose.onNodeWithText("导入 1 条记录").performClick()
            compose.onAllNodesWithText("确认导入").onLast().performClick()
            if(!ics) {
                compose.waitUntil(10000) { compose.onAllNodesWithText("导入 1 条记录").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("导入测试").assertExists()
                assertTrue(runBlocking { app.courseRepository.byTimetableOnce(id) }.isEmpty())
                app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_review")
                compose.onNodeWithText("导入 1 条记录").performClick()
                compose.onAllNodesWithText("确认导入").onLast().performClick()
            }
            compose.waitUntil(10000) { runBlocking { app.courseRepository.byTimetableOnce(id) }.size == 1 }
        } finally {
            app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_review")
            runBlocking {
                app.settingsRepository.setActiveTimetable(original.timetableId)
                app.database.courseDao().deleteByTimetable(id)
                app.database.courseDao().deleteTimetable(id)
            }
            source.delete()
        }
    }
}
