package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseManageListTest {
    @get:Rule val compose = createComposeRule()
    private fun course(id: Long) = Course(id = id, name = "课程$id", dayOfWeek = 1,
        startSection = 1, duration = 2, startWeek = 1, endWeek = 16, location = "A301")

    @Test fun longDayIsLazyAndDeletionKeepsItsTargetAfterListUpdate() {
        val courses = mutableStateOf((1L..100L).map(::course))
        var deleted: Long? = null
        compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
            CourseManageScreen(courses.value, 18, 12, {}, {}, { removed ->
                deleted = removed.id
                courses.value = courses.value.filterNot { it.id == removed.id }
            })
        } } }
        compose.onNodeWithTag("managed-course-100").assertDoesNotExist()
        compose.onNodeWithTag("courses-list").performScrollToNode(hasTestTag("managed-course-100"))
        compose.onNodeWithTag("managed-course-100").assertIsDisplayed()
        compose.onNode(hasContentDescription("删除") and hasAnyAncestor(hasTestTag("managed-course-100"))).performClick()
        compose.runOnIdle { courses.value = listOf(course(101)) + courses.value }
        compose.onNode(hasContentDescription("确认删除") and hasAnyAncestor(hasTestTag("managed-course-100"))).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(100L, deleted)
            assertTrue(courses.value.any { it.id == 101L })
            assertFalse(courses.value.any { it.id == 100L })
        }
        compose.onNodeWithTag("managed-course-100").assertDoesNotExist()
    }

    @Test fun multipleSessionsStayTogetherAndOpenWithBothArrangements() {
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val dao = app.database.courseDao()
        val table = kotlinx.coroutines.runBlocking {
            app.database.timetableDao().upsert(com.coursetable.app.data.Timetable(name = "manage-list-test-${System.nanoTime()}"))
        }
        try {
            val sessions = kotlinx.coroutines.runBlocking {
                val id = dao.upsert(course(1).copy(id = 0, timetableId = table))
                val first = dao.byId(id)!!
                val secondId = dao.upsert(first.copy(id = 0, dayOfWeek = 5, location = "B502"))
                listOf(first, dao.byId(secondId)!!)
            }
            compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
                CourseManageScreen(sessions, 18, 12, {}, {}, {})
            } } }
            compose.onNodeWithText("课程管理（共 1 门）").assertExists()
            compose.onNodeWithTag("managed-course-${sessions[1].id}").assertDoesNotExist()
            compose.onNodeWithText("B502", substring = true).assertExists()
            compose.onNodeWithText("课程1").performClick()
            compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
            compose.onNodeWithTag("course-session-choice-1").performScrollTo().performClick()
            compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("B502")
        } finally {
            kotlinx.coroutines.runBlocking {
                dao.deleteByTimetable(table)
                dao.deleteTimetable(table)
            }
        }
    }
}
