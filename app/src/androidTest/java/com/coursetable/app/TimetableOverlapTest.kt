package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.ui.LocalWallpaperActive
import com.coursetable.app.ui.LocalWallpaperCardOpacity
import com.coursetable.app.ui.TimetableCourseIndex
import com.coursetable.app.ui.TimetableGrid
import com.coursetable.app.ui.WeekFilter
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimetableOverlapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentCourseRemainsReadableAndTappableWhenAnInactiveCoursePartlyOverlaps() {
        val week = mutableStateOf(1)
        val showInactive = mutableStateOf(true)
        val current = Course(id = 1, name = "本周课", dayOfWeek = 1, startSection = 1,
            duration = 2, startWeek = 1, endWeek = 1)
        val overlapping = current.copy(id = 2, name = "后续课", startSection = 2, startWeek = 2, endWeek = 18)
        val adjacent = overlapping.copy(id = 3, name = "空位课", startSection = 4)
        var tapped: Course? = null
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    // Transparent cards would expose any inactive card still drawn underneath.
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalWallpaperActive provides true, LocalWallpaperCardOpacity provides 0.3f
                    ) {
                        val index = remember { TimetableCourseIndex(listOf(current, overlapping, adjacent)) }
                        val settings = AppSettings()
                        TimetableGrid(0.dp, 0.dp, settings, settings.semesterStart, week.value,
                            index.forWeek(week.value, showInactive.value, WeekFilter.ALL), false, 1,
                            { tapped = it }, { _, _ -> }, null)
                    }
                }
            }
        }
        compose.onNodeWithText("本周课").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(current, tapped) }
        compose.onNodeWithText("后续课").assertDoesNotExist()
        compose.onNodeWithText("空位课").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(adjacent, tapped)
            showInactive.value = false
        }
        compose.onNodeWithText("本周课").assertIsDisplayed()
        compose.onNodeWithText("空位课").assertDoesNotExist()
        compose.runOnIdle { week.value = 2 }
        compose.onNodeWithText("本周课").assertDoesNotExist()
        compose.onNodeWithText("后续课").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(overlapping, tapped) }
        compose.onNodeWithText("空位课").assertIsDisplayed()
    }
}
