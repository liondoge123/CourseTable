package com.coursetable.app

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.CourseTimeWheel
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WheelEmphasisTest {
    @get:Rule val compose = createComposeRule()
    @Test fun centerEmphasisMovesBeforeFingerReleaseAndValueCommit() {
        var committed = 1
        compose.setContent {
            var value by remember { mutableIntStateOf(1) }
            CourseTableTheme {
                CourseTimeWheel((1..6).toList(), value, { it.toString() },
                    Modifier.width(120.dp).testTag("wheel"), {}) { value = it; committed = it }
            }
        }
        compose.mainClock.autoAdvance = false
        val distance = with(compose.density) { 52.dp.toPx() }
        compose.onNodeWithTag("wheel", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, -distance), delayMillis = 48)
            moveBy(Offset(0f, -distance / 3), delayMillis = 48)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNode(hasText("2") and isSelected()).assertExists()
        compose.runOnIdle { assertEquals(1, committed) }
        compose.onNodeWithTag("wheel", useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}
