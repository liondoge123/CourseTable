package com.coursetable.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.RootDestination
import com.coursetable.app.ui.RootNavigationPager
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RootNavigationPagerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var pager: PagerState
    private var enabled by mutableStateOf(true)
    private var dialog by mutableStateOf(false)

    private fun show(initial: RootDestination = RootDestination.IMPORT, courseRows: Boolean = false) {
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    pager = rememberPagerState(initialPage = initial.ordinal, pageCount = { RootDestination.entries.size })
                    RootNavigationPager(pager, enabled) { page ->
                        if (page == RootDestination.COURSES && courseRows) {
                            CourseManageScreen(
                                listOf(Course(id = 1, name = "手势测试课程", dayOfWeek = 1, startSection = 1,
                                    duration = 2, startWeek = 1, endWeek = 16)),
                                18, 12, {}, {}, {}, rootMode = true
                            )
                        } else Box(Modifier.fillMaxSize().testTag("page-${page.name}")) { Text(page.name) }
                    }
                    if (dialog) AlertDialog({ dialog = false }, title = { Text("测试弹窗") }, confirmButton = {
                        val dismiss = LocalDialogDismissController.current
                        TextButton({ dismiss?.dismiss() }) { Text("关闭弹窗") }
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun selected(destination: RootDestination) = compose.runOnIdle {
        assertEquals(destination.ordinal, pager.settledPage)
        assertEquals(0f, pager.currentPageOffsetFraction, 0.001f)
    }

    @Test fun horizontalSwipesSelectAdjacentPagesAndStopAtTheLastPage() {
        show()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.COURSES)
    }

    @Test fun aShortDragFollowsTheFingerAndCancelsBackToItsPage() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.16f, 0f), delayMillis = 160)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue("Page must move while the finger is still down", abs(pager.currentPageOffsetFraction) > 0.05f) }
        compose.onNodeWithTag("root-pager").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        selected(RootDestination.IMPORT)
    }

    @Test fun courseRowSwipesNavigateWithoutDeletingOrOpeningTheEditor() {
        show(RootDestination.COURSES, courseRows = true)
        compose.onNodeWithTag("managed-course-1").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除课程").assertDoesNotExist()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.COURSES)
        compose.onNodeWithTag("managed-course-1").performTouchInput { swipeRight() }
        selected(RootDestination.TIMETABLE)
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
    }

    @Test fun reversingADragTowardTheTimetableCanReturnToCourses() {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveBy(Offset(width * 0.65f, 0f), delayMillis = 160)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(RootDestination.TIMETABLE.ordinal, pager.currentPage) }
        compose.onNodeWithTag("root-pager").performTouchInput {
            moveBy(Offset(-width * 0.65f, 0f), delayMillis = 160)
            cancel()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        selected(RootDestination.COURSES)
    }

    @Test fun timetableAndBlockedPagesDoNotNavigateOnHorizontalDrags() {
        show(RootDestination.TIMETABLE)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.TIMETABLE)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.TIMETABLE)
    }

    @Test fun blockingAndUnblockingAnOrdinaryPageRestoresItsGesture() {
        show()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
    }

    @Test fun draggingADialogDoesNotMoveThePageBehindIt() {
        show()
        compose.runOnIdle { dialog = true }
        compose.onNodeWithTag("overlay-glass-dialog").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithText("关闭弹窗").performClick()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
    }
}
