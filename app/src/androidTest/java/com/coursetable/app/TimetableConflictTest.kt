package com.coursetable.app

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimetableConflictTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
    private var originalTable: Long? = null
    private val createdTables = mutableListOf<Long>()
    private val visible = mutableStateOf(true)
    private val lifecycleOwner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
    private lateinit var vm: TimetableViewModel
    private var sessions = emptyList<Course>()

    private fun show(count: Int = 2, large: Boolean = false) {
        runBlocking {
            originalTable = app.settingsRepository.settings.first().timetableId
            val today = LocalDate.now()
            val monday = today.minusDays(today.dayOfWeek.value - 1L)
            val table = app.timetableRepository.create("冲突合并回归", Timetable(
                name = "冲突合并回归", semesterStartEpochDay = monday.toEpochDay()))
            createdTables += table
            repeat(count) { index ->
                app.courseRepository.save(Course(timetableId = table, name = "冲突课程${index + 1}",
                    teacher = "教师${index + 1}", location = "教室${index + 1}", dayOfWeek = 1,
                    startSection = if (index == 0) 1 else 2, duration = 2, startWeek = 1, endWeek = 18))
            }
            sessions = app.courseRepository.byTimetableOnce(table).sortedBy { it.id }
            app.settingsRepository.setActiveTimetable(table)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            if (visible.value) {
                vm = viewModel { TimetableViewModel(app) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner,
                    LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                    CourseTableTheme(themeMode = if (large) ThemeMode.DARK else ThemeMode.LIGHT) {
                        LiquidBackdropHost(Modifier.width(if (large) 320.dp else 400.dp).fillMaxHeight()) { TimetableScreen(vm) }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { ::vm.isInitialized && vm.latestSettings.timetableId == createdTables.first() && vm.courses.value.size == count }
        compose.onNodeWithText("${count}门冲突").assertIsDisplayed()
    }

    private fun open(count: Int) {
        compose.onNodeWithText("${count}门冲突").performClick()
        compose.onNodeWithText("冲突课程（$count 门）").assertIsDisplayed()
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
    }

    @After fun restore() {
        compose.mainClock.autoAdvance = true
        if (::vm.isInitialized) compose.runOnIdle { visible.value = false }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                lifecycleOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
            }
        }
        runBlocking {
            originalTable?.let { app.settingsRepository.setActiveTimetable(it) }
            createdTables.forEach { app.timetableRepository.delete(it) }
        }
    }

    @Test fun everyOccurrenceCanBeOpenedAndDetailsWaitForTheChooserToClose() {
        show()
        for (session in sessions) {
            open(2)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("timetable-conflict-choice-${session.id}").performClick()
            compose.runOnIdle { assertNull(vm.detailCourse) }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { assertNull(vm.detailCourse) }
            compose.mainClock.advanceTimeBy(500)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
            compose.onNodeWithContentDescription("删除当前时段").assertIsDisplayed()
            compose.onNodeWithText(session.location).assertIsDisplayed()
            compose.runOnIdle { assertEquals(session.id, vm.detailCourse?.id) }
            back()
            compose.runOnIdle { assertNull(vm.detailCourse) }
        }
        open(2)
        compose.onNodeWithTag("timetable-conflict-choice-${sessions.last().id}").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.runOnIdle {
            assertEquals(sessions.last().id, vm.editingCourse?.id)
            assertTrue(vm.editorOpen)
            vm.closeEditor()
        }
    }

    @Test fun manyCoursesScrollOnANarrowDarkScreenWithLargeFonts() {
        show(count = 12, large = true)
        compose.onNodeWithText(sessions.last().name).assertDoesNotExist()
        open(12)
        compose.onNodeWithTag("timetable-conflict-list", useUnmergedTree = true).performScrollToIndex(11)
        compose.onNodeWithTag("timetable-conflict-choice-${sessions.last().id}").assertIsDisplayed().performClick()
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(sessions.last().id, vm.detailCourse?.id) }
    }

    @Test fun cancelBackAndContextChangesNeverOpenAnUnselectedOrStaleCourse() {
        show()
        open(2)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        open(2)
        back()
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        open(2)
        compose.runOnIdle { vm.setWeek(2) }
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        compose.runOnIdle { vm.setWeek(1) }
        open(2)
        compose.runOnIdle { vm.setFilter(WeekFilter.ODD) }
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        open(2)
        runBlocking { app.courseRepository.save(sessions.first().copy(location = "教室已变化")) }
        compose.waitUntil(10_000) { vm.courses.value.any { it.location == "教室已变化" } }
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        open(2)
        val nextTable = runBlocking { app.timetableRepository.create("冲突测试第二课表") }
        createdTables += nextTable
        compose.runOnIdle { vm.switchTimetable(nextTable) }
        compose.waitUntil(10_000) { vm.latestSettings.timetableId == nextTable }
        compose.onNodeWithTag("timetable-conflict-picker").assertDoesNotExist()
        compose.runOnIdle { assertNull(vm.detailCourse) }
    }

    @Test fun deletingAChosenOccurrenceRestoresTheRemainingSingleCard() {
        show()
        open(2)
        compose.onNodeWithTag("timetable-conflict-choice-${sessions.first().id}").performClick()
        compose.onNodeWithContentDescription("删除当前时段").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(10_000) { vm.courses.value.size == 1 && vm.detailCourse == null }
        compose.onNodeWithText("2门冲突").assertDoesNotExist()
        compose.onNodeWithText(sessions.last().name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(sessions.last().id, vm.detailCourse?.id) }
    }

    @Test fun transparentConflictCardKeepsItsCountAndTapTarget() {
        val block = timetableCourseBlocks((1..3).map { index -> TimetableCourseItem(
            Course(id = index.toLong(), name = "课程$index", dayOfWeek = 1, startSection = 1,
                duration = 1, startWeek = 1, endWeek = 18), true) }).single()
        var opacity by mutableFloatStateOf(1f)
        var taps = 0
        compose.setContent {
            CourseTableTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalWallpaperActive provides true, LocalWallpaperCardOpacity provides opacity) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        TimetableConflictCardSurface(block, Modifier.size(100.dp, 100.dp)) { taps++ }
                    }
                }
            }
        }
        val tag = "timetable-conflict-${block.key}"
        val opaque = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        compose.runOnIdle { opacity = 0f }
        val clear = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        assertTrue(opaque[opaque.width - 8, opaque.height - 8].luminance() > .5f)
        assertTrue(clear[clear.width - 8, clear.height - 8].luminance() < .1f)
        compose.onNodeWithText("3门冲突").assertIsDisplayed()
        compose.onNodeWithText("课程3").assertDoesNotExist()
        compose.onNodeWithTag(tag).performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    private fun verifyNameSpace(longFirst: Boolean, fontScale: Float = 1f) {
        val names = if (longFirst) listOf("模拟电子技术基础与综合实验".repeat(10), "大学体育综合训练")
            else listOf("模拟电子技术基础", "大学体育综合训练")
        val block = timetableCourseBlocks(names.mapIndexed { index, name -> TimetableCourseItem(
            Course(id = index + 1L, name = name, dayOfWeek = 1, startSection = 1,
                duration = 4, startWeek = 1, endWeek = 18), true) }).single()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CourseTableTheme {
                    TimetableConflictCardSurface(block, Modifier.size(if (fontScale > 1f) 54.dp else 44.dp, 244.dp)) {}
                }
            }
        }
        val firstBounds = compose.onNodeWithTag("timetable-conflict-name-slot-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("timetable-conflict-name-slot-2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(firstBounds.height, secondBounds.height, 1f)
        assertTrue(firstBounds.bottom <= secondBounds.top)
        val layouts = (1..2).map { id ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("timetable-conflict-name-$id", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.single()
        }
        assertTrue("Second name should use more than the old two-line limit", layouts[1].lineCount > 2)
        assertFalse("The second course must retain enough space for its complete name", layouts[1].hasVisualOverflow)
        if (longFirst) assertTrue("An extreme name truncates inside its own allocation", layouts[0].hasVisualOverflow)
        else assertFalse("Both normal names should fit in a tall card", layouts[0].hasVisualOverflow)
    }

    @Test fun tallCardsUseAvailableHeightToShowBothCompleteNames() = verifyNameSpace(false)
    @Test fun extremeFirstNameDoesNotTakeSpaceFromTheSecondCourse() = verifyNameSpace(true)
    @Test fun largeFontsStillReceiveEqualCourseNameAllocations() = verifyNameSpace(true, 1.5f)
}
