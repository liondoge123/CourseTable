package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.ui.CourseWeeksDialog
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.data.withScheduledWeeks
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import com.coursetable.app.ui.theme.LiquidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

class CourseWeeksDialogTest {
    @get:Rule val compose = createComposeRule()
    private var saved: List<Int>? = null
    private fun show(total: Int = 18, initial: List<Int> = (1..18).toList()) {
        val dark = InstrumentationRegistry.getArguments().getString("weeksDark") == "true"
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            CourseTableTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                    if (open) CourseWeeksDialog(total, initial, { saved = it }, { open = false })
                }
            }
        }
        compose.waitForIdle()
        captureEvidence("${if (dark) "dark" else "light"}-initial-$total")
    }

    private fun captureEvidence(label: String) {
        val session = InstrumentationRegistry.getArguments().getString("weeksEvidenceSession") ?: return
        require(session.matches(Regex("[a-zA-Z0-9-]+")))
        val path = "/data/local/tmp/coursetable-testing/$session/$label.png"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $path")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    @Test fun presetAndIndividualTogglesConfirmExactWeeks() {
        show()
        compose.onNodeWithTag("weeks-preset-单周").performClick()
        compose.onNodeWithTag("course-week-1").assertIsOn().performClick()
        compose.onNodeWithTag("course-week-2").assertIsOff().performClick()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(2, 3, 5, 7, 9, 11, 13, 15, 17), saved)
    }

    @Test fun cancellationDoesNotCommitDraft() {
        show(initial = listOf(1, 4, 8))
        compose.onNodeWithTag("weeks-preset-双周").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertNull(saved)
        compose.onNodeWithTag("course-weeks-dialog").assertDoesNotExist()
    }

    @Test fun emptySelectionDisablesConfirmAndSingleWeekDisablesEvenPreset() {
        show(total = 1, initial = listOf(1))
        compose.onNodeWithTag("weeks-preset-双周").assertIsNotEnabled()
        compose.onNodeWithTag("course-week-1").performClick()
        compose.onNodeWithTag("course-weeks-confirm").assertIsNotEnabled()
        compose.onNodeWithText("请至少选择一周").assertIsDisplayed()
        compose.onNodeWithTag("weeks-preset-全选").performClick()
        compose.onNodeWithTag("course-weeks-confirm").assertIsEnabled()
    }

    @Test fun sixtyWeeksCanScrollToFinalWeek() {
        show(total = 60, initial = listOf(1))
        compose.onNodeWithTag("course-weeks-grid", useUnmergedTree = true).performScrollToIndex(59)
        compose.waitForIdle()
        captureEvidence("last-week-60")
        compose.onNodeWithTag("course-week-60").assertIsDisplayed().performClick()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 60), saved)
    }

    @Test fun editorReopensCommittedSelectionAndSavesExactWeeks() {
        var result: Course? = null
        val original = Course(name = "数学", dayOfWeek = 1, startSection = 1, duration = 1,
            startWeek = 1, endWeek = 18).withScheduledWeeks(listOf(1, 4, 8))
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                    CourseEditorDialog(original, 18, 12, {}, { result = it })
                }
            }
        }
        compose.onNodeWithTag("course-editor-weeks").performScrollTo().performClick()
        compose.onNodeWithTag("course-week-4").assertIsOn().performClick()
        compose.onNodeWithTag("course-week-5").assertIsOff().performClick()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("course-editor-weeks").performClick()
        compose.onNodeWithTag("course-week-4").assertIsOff()
        compose.onNodeWithTag("course-week-5").assertIsOn()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 5, 8), result!!.scheduledWeeks())
    }

    private fun dragAcross(first: Int, last: Int, returnToStart: Boolean = false) {
        val grid = compose.onNodeWithTag("course-weeks-grid", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("course-week-$first").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        val end = compose.onNodeWithTag("course-week-$last").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("course-weeks-grid", useUnmergedTree = true).performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(start)
            repeat(12) { step -> moveTo(start + (end - start) * ((step + 1f) / 12), delayMillis = 16) }
            if (returnToStart) repeat(12) { step -> moveTo(end + (start - end) * ((step + 1f) / 12), delayMillis = 16) }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun longDragSelectsCrossedWeeksWithoutTogglingOnReentry() {
        show(initial = listOf(18))
        dragAcross(1, 3, returnToStart = true)
        compose.onNodeWithTag("course-week-1").assertIsOn()
        compose.onNodeWithTag("course-week-2").assertIsOn()
        compose.onNodeWithTag("course-week-3").assertIsOn()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 2, 3, 18), saved)
    }

    @Test fun longDragStartingOnSelectedWeekClearsCrossedWeeks() {
        show()
        dragAcross(1, 3)
        compose.onNodeWithTag("course-week-1").assertIsOff()
        compose.onNodeWithTag("course-week-2").assertIsOff()
        compose.onNodeWithTag("course-week-3").assertIsOff()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals((4..18).toList(), saved)
    }

    @Test fun ordinarySwipeScrollsWithoutPaintingWeeks() {
        show(total = 60, initial = listOf(1))
        compose.onNodeWithTag("course-weeks-grid", useUnmergedTree = true).performTouchInput { swipeUp() }
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), saved)
    }

    @Test fun stationaryLongPressOnSelectedWeekStaysOffAfterRelease() {
        show()
        compose.onNodeWithTag("course-week-1").performTouchInput { longClick() }
        compose.onNodeWithTag("course-week-1").assertIsOff()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals((2..18).toList(), saved)
    }

    @Test fun stationaryLongPressOnUnselectedWeekStaysOnAndNextClickWorks() {
        show(initial = listOf(18))
        compose.onNodeWithTag("course-week-1").performTouchInput { longClick() }
        compose.onNodeWithTag("course-week-1").assertIsOn().performClick()
        compose.onNodeWithTag("course-week-1").assertIsOff()
        compose.onNodeWithTag("course-weeks-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(18), saved)
    }

}
