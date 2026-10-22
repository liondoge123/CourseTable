package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.CourseTimeDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseTimeDialogTest {
    @get:Rule val compose = createComposeRule()
    private var saved: Triple<Int, Int, Int>? = null
    private fun show(start: Int = 2, end: Int = 3, count: Int = 12) {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                    if (open) CourseTimeDialog(2, start, end, count,
                        { day, first, last -> saved = Triple(day, first, last) }, { open = false })
                }
            }
        }
        compose.waitForIdle()
        val session = InstrumentationRegistry.getArguments().getString("timeEvidenceSession")
        if (session != null) {
            require(session.matches(Regex("[a-zA-Z0-9-]+")))
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screencap -p /data/local/tmp/coursetable-testing/$session/time-$count.png")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun choose(wheel: String, index: Int, label: String) {
        compose.onNodeWithTag(wheel, useUnmergedTree = true).performScrollToIndex(index)
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(wheel)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test fun initialValuesConfirmWithoutChanges() {
        show()
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(Triple(2, 2, 3), saved)
    }

    private fun endOption(section: Int) = compose.onNode(
        hasTestTag("第 $section 节-time-option") and hasAnyAncestor(hasTestTag("course-time-end")), useUnmergedTree = true)

    @Test fun advancingStartMovesEndAndRestrictsEndOptions() {
        show()
        choose("course-time-start", 5, "第 6 节")
        endOption(6).assertIsSelected()
        compose.onNodeWithTag("course-time-end", useUnmergedTree = true).performTouchInput { swipeDown() }
        compose.waitForIdle()
        (1..5).forEach { endOption(it).assertDoesNotExist() }
        endOption(6).assertIsSelected()
        choose("course-time-day", 6, "周日")
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(Triple(7, 6, 6), saved)
    }

    @Test fun changingStartPreservesLaterEndAndRestoresEarlierEndOptions() {
        show(start = 4, end = 10)
        choose("course-time-start", 6, "第 7 节")
        endOption(10).assertIsSelected()
        choose("course-time-start", 1, "第 2 节")
        endOption(10).assertIsSelected()
        choose("course-time-end", 1, "第 3 节")
        endOption(3).assertIsSelected()
        compose.onNode(hasTestTag("第 2 节-time-option") and hasAnyAncestor(hasTestTag("course-time-start")),
            useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(Triple(2, 2, 3), saved)
    }

    @Test fun lastStartPeriodLeavesOnlyTheSameEndPeriod() {
        show(start = 12, end = 12)
        endOption(12).assertIsSelected()
        (1..11).forEach { endOption(it).assertDoesNotExist() }
        compose.onNodeWithTag("course-time-end", useUnmergedTree = true).performTouchInput { swipeDown() }
        compose.waitForIdle()
        endOption(12).assertIsSelected()
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(Triple(2, 12, 12), saved)
    }

    @Test fun cancellationDoesNotCommitDraft() {
        show()
        choose("course-time-day", 4, "周五")
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertNull(saved)
        compose.onNodeWithTag("course-time-dialog").assertDoesNotExist()
    }

    @Test fun draggingWheelCommitsSnappedValue() {
        show()
        compose.onNodeWithTag("course-time-start", useUnmergedTree = true).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("course-time-confirm").assertIsEnabled().performClick()
        compose.waitForIdle()
        val result = saved!!
        assertTrue("Dragging must advance the start wheel", result.second > 2)
        assertTrue(result.second in 1..12)
        assertTrue(result.third >= result.second && result.third <= 12)
    }

    @Test fun onePeriodAndLastPeriodRemainValid() {
        show(start = 1, end = 1, count = 1)
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(Triple(2, 1, 1), saved)
    }

    @Test fun editorSavesEndAsInclusiveDuration() {
        var result: Course? = null
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                    CourseEditorDialog(Course(name = "数学", dayOfWeek = 2, startSection = 2,
                        duration = 2, startWeek = 1, endWeek = 18), 18, 12, {}, { result = it })
                }
            }
        }
        compose.onNodeWithTag("course-editor-time").performScrollTo().performClick()
        choose("course-time-end", 3, "第 5 节")
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("course-editor-time").performClick()
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitForIdle()
        val savedCourse = result!!
        assertEquals(2, savedCourse.startSection)
        assertEquals(4, savedCourse.duration)
        assertEquals(2, savedCourse.dayOfWeek)
    }
}
