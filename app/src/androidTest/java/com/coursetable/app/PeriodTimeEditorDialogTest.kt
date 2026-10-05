package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.ui.PeriodTimeEditorDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PeriodTimeEditorDialogTest {
    @get:Rule val compose = createComposeRule()
    private fun time(start: String, end: String) = PeriodTime(LocalTime.parse(start), LocalTime.parse(end))
    private val initial = listOf(time("08:00", "08:45"), time("08:55", "09:45"), time("14:00", "14:45"))
    private var saved: List<PeriodTime>? = null
    private fun show() {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                    if (open) PeriodTimeEditorDialog(initial, 0, { saved = it }, { open = false })
                }
            }
        }
        compose.waitForIdle()
    }
    private fun choose(tag: String, value: Int) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollToIndex(value)
        compose.onNode(hasText(value.toString().padStart(2, '0')) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }
    private fun capture(label: String) {
        compose.mainClock.advanceTimeBy(350)
        compose.waitForIdle()
        val session = InstrumentationRegistry.getArguments().getString("periodEvidenceSession") ?: return
        require(session.matches(Regex("[a-zA-Z0-9-]+")))
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/coursetable-testing/$session/$label.png")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
    @Test fun customEndDoesNotShiftFollowingAndStartPreservesActualDuration() {
        show()
        capture("period-editor")
        choose("period-end-minute", 50)
        choose("period-start-hour", 7)
        choose("period-start-minute", 5)
        compose.onNodeWithTag("period-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(time("07:05", "07:55"), saved!![0])
        assertEquals(initial.drop(1), saved!!.drop(1))
    }
    @Test fun customEndKeepsAllFollowingPeriodsUnchanged() {
        show()
        choose("period-end-minute", 50)
        compose.onNodeWithTag("period-adjust-following").assertDoesNotExist()
        compose.onNodeWithTag("period-time-confirm").performClick()
        compose.waitForIdle()
        assertEquals(time("08:00", "08:50"), saved!![0])
        assertEquals(initial.drop(1), saved!!.drop(1))
    }
    @Test fun overlapBlocksConfirmUntilCurrentPeriodIsCorrected() {
        show()
        choose("period-end-hour", 9)
        choose("period-end-minute", 0)
        compose.onNodeWithText("第 1 节与第 2 节重叠 5 分钟").assertExists()
        compose.onNodeWithTag("period-time-confirm").assertIsNotEnabled()
        choose("period-end-hour", 8)
        choose("period-end-minute", 45)
        compose.onNodeWithTag("period-time-confirm").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(initial, saved)
    }
    @Test fun invalidEndAndCancellationDoNotCommit() {
        show()
        choose("period-end-hour", 7)
        compose.onNodeWithTag("period-time-confirm").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertNull(saved)
        compose.onNodeWithTag("period-time-editor").assertDoesNotExist()
    }
}
