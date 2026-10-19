package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.TimetableScreen
import com.coursetable.app.ui.TimetableViewModel
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimetableDateTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
    private val today = LocalDate.now()
    private val monday = today.minusDays(today.dayOfWeek.value - 1L)
    private val dateFormat = DateTimeFormatter.ofPattern("M/d")
    private var originalId: Long? = null
    private var originalCardAlignLeft: Boolean? = null
    private val createdIds = mutableListOf<Long>()
    private val visible = mutableStateOf(true)
    private val lifecycleOwner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
    private lateinit var vm: TimetableViewModel

    private fun show() {
        runBlocking {
            val original = app.settingsRepository.settings.first()
            originalId = original.timetableId
            originalCardAlignLeft = original.cardAlignLeft
            val id = app.timetableRepository.create("日期回归测试", Timetable(
                name = "日期回归测试", semesterStartEpochDay = monday.minusWeeks(5).plusDays(1).toEpochDay(), totalWeeks = 18
            ))
            createdIds += id
            app.settingsRepository.setActiveTimetable(id)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            if (visible.value) {
                vm = viewModel { TimetableViewModel(app) }
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    CourseTableTheme {
                        LiquidBackdropHost(Modifier.fillMaxSize()) { TimetableScreen(vm) }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { ::vm.isInitialized && vm.latestSettings.timetableId == createdIds.first() }
        assertWeek(6)
    }

    private fun assertWeek(week: Int) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("timetable-week-controls") and hasAnyDescendant(hasText("第 $week 周")))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertEquals(week, vm.displayedWeek()) }
    }

    private fun assertMonday(date: LocalDate) {
        compose.onNode(hasTestTag("timetable-weekdays") and hasAnyDescendant(hasText(date.format(dateFormat))))
            .assertExists()
    }

    private fun captureForVisualReview() {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$").matches(directory))
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $directory/timetable.png").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
    }

    @After fun restoreTables() {
        if (::vm.isInitialized) compose.runOnIdle { visible.value = false }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
        }
        runBlocking {
            originalId?.let { app.settingsRepository.setActiveTimetable(it) }
            originalCardAlignLeft?.let { app.settingsRepository.save(cardAlignLeft = it) }
            createdIds.forEach { app.timetableRepository.delete(it) }
        }
    }

    @Test fun changingSemesterReanchorsPagerAndPreservesTheRealCalendarDate() {
        show()
        captureForVisualReview()
        assertMonday(monday)
        compose.onNodeWithContentDescription("下一周").performClick()
        assertWeek(7)
        assertMonday(monday.plusWeeks(1))
        runBlocking { app.settingsRepository.save(cardAlignLeft = !vm.latestSettings.cardAlignLeft) }
        assertWeek(7)
        runBlocking { app.settingsRepository.save(semesterStart = monday.minusWeeks(2).plusDays(3)) }
        assertWeek(3)
        assertMonday(monday)
        compose.runOnIdle { assertEquals(today, vm.today) }
    }

    @Test fun dateRolloverFollowsThisWeekWhileManualBrowsingIsPreserved() {
        show()
        compose.runOnIdle { vm.refreshDate(monday.minusDays(1)) }
        assertWeek(5)
        assertMonday(monday.minusWeeks(1))
        compose.runOnIdle { vm.refreshDate(monday) }
        assertWeek(6)
        assertMonday(monday)
        compose.onNodeWithContentDescription("下一周").performClick()
        assertWeek(7)
        compose.runOnIdle { vm.refreshDate(monday.plusDays(1)) }
        assertWeek(7)
        compose.runOnIdle { vm.refreshDate(monday.plusWeeks(1)) }
        assertWeek(7)
        assertMonday(monday.plusWeeks(1))
        compose.runOnIdle { vm.refreshDate(monday.plusWeeks(2)) }
        assertWeek(8)
        assertMonday(monday.plusWeeks(2))
    }

    @Test fun outsideSemesterDoesNotOfferAnUnrelatedWeekAsToday() {
        show()
        runBlocking { app.settingsRepository.save(semesterStart = monday.plusWeeks(2)) }
        assertWeek(1)
        compose.runOnIdle { assertTrue(vm.todayWeekNumber() < 1) }
        compose.onNodeWithText("第 1 周").performClick()
        compose.onNodeWithText("回到本周", substring = true).assertDoesNotExist()
        compose.onNodeWithText("选择周次").assertExists()
    }

    @Test fun afterSemesterDoesNotOfferTheLastWeekAsToday() {
        show()
        runBlocking { app.settingsRepository.save(totalWeeks = 3) }
        assertWeek(3)
        compose.runOnIdle { assertEquals(6, vm.todayWeekNumber()) }
        compose.onNodeWithText("第 3 周").performClick()
        compose.onNodeWithText("回到本周", substring = true).assertDoesNotExist()
    }

    @Test fun returningToForegroundReadsTheSystemDateAgain() {
        show()
        compose.runOnIdle { lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { vm.refreshDate(monday.minusDays(1)) }
        assertWeek(5)
        compose.runOnIdle { lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED }
        assertWeek(6)
        compose.runOnIdle { assertEquals(LocalDate.now(), vm.today) }
        assertMonday(monday)
    }

    @Test fun switchingTimetablesSelectsTheNewTablesActualWeek() {
        show()
        val nextId = runBlocking {
            app.timetableRepository.create("第二张日期测试课表", Timetable(
                name = "第二张日期测试课表", semesterStartEpochDay = monday.minusWeeks(1).plusDays(6).toEpochDay()
            )).also { createdIds += it }
        }
        compose.onNodeWithContentDescription("下一周").performClick()
        assertWeek(7)
        compose.runOnIdle { vm.switchTimetable(nextId) }
        assertWeek(2)
        assertMonday(monday)
    }
}
