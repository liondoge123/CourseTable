package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.RootNavigationPager
import com.coursetable.app.ui.RootDestination
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseManageListTest {
    @get:Rule val compose = createComposeRule()
    private fun course(id: Long) = Course(id = id, name = "课程$id", dayOfWeek = 1,
        startSection = 1, duration = 2, startWeek = 1, endWeek = 16, location = "A301")

    private fun show(
        courses: State<List<Course>> = mutableStateOf(listOf(course(1))),
        onDelete: (Course) -> Unit = {}
    ) {
        compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
            CourseManageScreen(courses.value, 18, 12, {}, {}, onDelete)
        } }
        }
    }

    @Test fun longDayIsLazyAndEditorDeletionKeepsItsTargetAfterListUpdate() {
        val courses = mutableStateOf((1L..100L).map(::course))
        var deleted: Long? = null
        show(courses, onDelete = { removed ->
            deleted = removed.id
            courses.value = courses.value.filterNot { it.id == removed.id }
        })
        compose.onNodeWithTag("managed-course-100").assertDoesNotExist()
        compose.onNodeWithTag("courses-list").performScrollToNode(hasTestTag("managed-course-100"))
        compose.onNodeWithTag("managed-course-100").assertIsDisplayed().performClick()
        compose.onNodeWithTag("course-editor-name").assertTextContains("课程100")
        compose.runOnIdle { courses.value = listOf(course(101)) + courses.value }
        compose.onNodeWithTag("course-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("删除整门课程？").assertIsDisplayed()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5000) { deleted != null }
        compose.runOnIdle {
            assertEquals(100L, deleted)
            assertTrue(courses.value.any { it.id == 101L })
            assertFalse(courses.value.any { it.id == 100L })
        }
    }

    @Test fun horizontalDragsHaveNoDeleteActionOrAccidentalEditInEitherLayoutDirection() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var deletions = 0
        lateinit var pager: PagerState
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
            CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
                pager = rememberPagerState(initialPage = RootDestination.COURSES.ordinal,
                    pageCount = { RootDestination.entries.size })
                RootNavigationPager(pager, true) { page ->
                    if (page == RootDestination.COURSES) CourseManageScreen(
                        listOf(course(1)), 18, 12, {}, {}, { deletions++ }, rootMode = true
                    ) else Box(Modifier.fillMaxSize()) { Text(page.name) }
                }
            } }
        } }
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layout }
            compose.onNodeWithTag("managed-course-1").performTouchInput {
                if (layout == LayoutDirection.Ltr) swipeLeft() else swipeRight()
            }
            compose.runOnIdle { assertEquals(RootDestination.IMPORT.ordinal, pager.settledPage) }
            compose.onNodeWithTag("root-pager").performTouchInput {
                if (layout == LayoutDirection.Ltr) swipeRight() else swipeLeft()
            }
            compose.runOnIdle { assertEquals(RootDestination.COURSES.ordinal, pager.settledPage) }
            compose.onNodeWithContentDescription("删除课程").assertDoesNotExist()
            compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
            compose.onNodeWithTag("managed-course-1").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, deletions) }
        }
    }

    @Test fun tappingACourseEditsAndBottomDeletionRequiresConfirmation() {
        var deletions = 0
        show(onDelete = { deletions++ })
        compose.onNodeWithTag("managed-course-1").performClick()
        compose.onNodeWithTag("course-editor-name").assertIsDisplayed().assertTextContains("课程1")
        compose.onNodeWithTag("course-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("删除整门课程？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithTag("course-editor-name").assertExists()
        compose.onNodeWithTag("course-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5000) { deletions == 1 }
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
    }

    @Test fun verticalScrollingDoesNotOpenAnEditorOrDeleteAnything() {
        var deletions = 0
        show(mutableStateOf((1L..30L).map(::course)), onDelete = { deletions++ })
        compose.onNodeWithTag("courses-list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("courses-list").performScrollToNode(hasTestTag("managed-course-30"))
        compose.onNodeWithTag("managed-course-30").assertIsDisplayed()
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除课程").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deletions) }
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
