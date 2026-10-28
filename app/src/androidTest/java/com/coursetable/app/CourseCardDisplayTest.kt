package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.SettingsScreen
import com.coursetable.app.ui.TimetableCourseCardSurface
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseCardDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibilityAndOrderUpdateIndependentlyWithoutChangingCourseData() {
        var teacherVisible by mutableStateOf(true)
        var locationVisible by mutableStateOf(true)
        var locationFirst by mutableStateOf(false)
        compose.setContent {
            CourseTableTheme {
                TimetableCourseCardSurface("高等数学", "张老师", "教学楼2102", Color.Blue, 2,
                    Modifier.size(48.dp, 121.dp), showTeacher = teacherVisible,
                    showLocation = locationVisible, locationFirst = locationFirst, onClick = {})
            }
        }
        fun assertOrder(first: String, second: String) {
            assertTrue(compose.onNodeWithText(first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom <=
                compose.onNodeWithText(second, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top)
        }
        assertOrder("张老师", "教学楼2102")
        compose.runOnIdle { locationFirst = true }
        assertOrder("教学楼2102", "张老师")
        capture("location-first")
        compose.runOnIdle { teacherVisible = false }
        compose.onNodeWithText("张老师", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("教学楼2102", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { locationVisible = false; teacherVisible = true }
        compose.onNodeWithText("教学楼2102", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("张老师", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { locationVisible = true }
        assertOrder("教学楼2102", "张老师")
    }

    private fun showShortCard(fontScale: Float = 1f, showLocation: Boolean = true, shortName: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CourseTableTheme {
                    TimetableCourseCardSurface(if (shortName) "数学" else "计算机组成原理与体系结构", "张老师", "教学楼2102",
                        Color.Blue, 1, Modifier.size(46.dp, 59.dp).testTag("card"),
                        showLocation = showLocation, onClick = {})
                }
            }
        }
    }

    @Test fun shortCardKeepsTwoLineLocationAndDropsTeacherWhenNameIsLong() {
        showShortCard()
        compose.onNodeWithText("计算机组成原理与体系结构", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("教学楼2102", useUnmergedTree = true).assertIsDisplayed()
        capture("short-card")
        compose.onNodeWithText("张老师", useUnmergedTree = true).assertIsNotDisplayed()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("教学楼2102", useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse("The location must fit rather than be clipped by the card", results.single().hasVisualOverflow)
        assertInsideCard("教学楼2102")
    }

    @Test fun shortCardStillShowsTeacherWhenThereIsRoom() {
        showShortCard(showLocation = false, shortName = true)
        compose.onNodeWithText("张老师", useUnmergedTree = true).assertIsDisplayed()
        assertInsideCard("张老师")
    }

    @Test fun largeFontKeepsLocationInsideShortCard() {
        showShortCard(fontScale = 1.5f)
        compose.onNodeWithText("计算机组成原理与体系结构", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("教学楼2102", useUnmergedTree = true).assertIsDisplayed()
        capture("large-font")
        assertInsideCard("教学楼2102")
    }

    private fun assertInsideCard(text: String) {
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(field.top >= card.top && field.bottom <= card.bottom)
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(directory.matches(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$")))
        compose.waitForIdle()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $directory/$name.png")).use { it.readBytes() }
    }

    @Test fun preferencesKeepDefaultsAndSurviveRestartTableSwitchAndSaveAll() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            val defaults = env.settings.settings.first()
            assertTrue(defaults.cardShowTeacher)
            assertTrue(defaults.cardShowLocation)
            assertFalse(defaults.cardLocationFirst)
            env.settings.save(cardShowTeacher = false, cardShowLocation = false, cardLocationFirst = true)
            env.settings.save(themeMode = "dark")
            env.database.timetableDao().upsert(Timetable(id = 101, name = "秋季"))
            env.database.timetableDao().upsert(Timetable(id = 102, name = "春季"))
            env.settings.setActiveTimetable(101)
            env.settings.setActiveTimetable(102)
            val restarted = SettingsRepository(env.context, env.database.timetableDao(), env.preferences)
            val saved = restarted.settings.first()
            assertFalse(saved.cardShowTeacher)
            assertFalse(saved.cardShowLocation)
            assertTrue(saved.cardLocationFirst)
            restarted.saveAll(saved.copy(cardShowTeacher = true))
            val restored = restarted.settings.first()
            assertTrue(restored.cardShowTeacher)
            assertFalse(restored.cardShowLocation)
            assertTrue(restored.cardLocationFirst)
        }
    }

    @Test fun settingsControlsPersistBothSwitchesAndChosenOrder() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val original = runBlocking { app.settingsRepository.settings.first() }
        try {
            compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) { SettingsScreen() } } }
            compose.onNodeWithText("课程卡片设置").performScrollTo().performClick()
            compose.onNodeWithText("显示教师").performScrollTo().performClick()
            compose.waitUntil(5000) {
                runBlocking { app.settingsRepository.settings.first().cardShowTeacher != original.cardShowTeacher }
            }
            compose.onNodeWithText("显示地点").performScrollTo().performClick()
            compose.waitUntil(5000) {
                runBlocking { app.settingsRepository.settings.first().cardShowLocation != original.cardShowLocation }
            }
            compose.onNodeWithText("教师与地点顺序").performScrollTo().performClick()
            capture("settings-order")
            val next = if (original.cardLocationFirst) "teacher-first" else "location-first"
            compose.onNodeWithTag("card-info-order-$next").performClick()
            compose.waitUntil(5000) {
                runBlocking { app.settingsRepository.settings.first().cardLocationFirst != original.cardLocationFirst }
            }
        } finally {
            runBlocking { app.settingsRepository.save(cardShowTeacher = original.cardShowTeacher,
                cardShowLocation = original.cardShowLocation, cardLocationFirst = original.cardLocationFirst) }
        }
    }
}
