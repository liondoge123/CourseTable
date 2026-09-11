package com.coursetable.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coursetable.app.ui.InlineDeleteAction
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InlineDeleteActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labeledActionRequiresTwoClicks() {
        var confirmCount = 0
        composeRule.setContent {
            var armed by remember { mutableStateOf(false) }
            CourseTableTheme {
                InlineDeleteAction(
                    armed = armed,
                    onArm = { armed = true },
                    onConfirm = { confirmCount++ }
                )
            }
        }

        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle { assertEquals(0, confirmCount) }
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmCount) }
    }

    @Test
    fun compactActionKeepsTrashSemanticsWhileArmed() {
        var confirmCount = 0
        composeRule.setContent {
            var armed by remember { mutableStateOf(false) }
            CourseTableTheme {
                InlineDeleteAction(
                    armed = armed,
                    onArm = { armed = true },
                    onConfirm = { confirmCount++ },
                    compact = true
                )
            }
        }

        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.runOnIdle { assertEquals(0, confirmCount) }
        composeRule.onNodeWithContentDescription("确认删除").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmCount) }
    }

    @Test
    fun confirmationAutomaticallyResetsAfterFiveSeconds() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var armed by remember { mutableStateOf(false) }
            LaunchedEffect(armed) {
                if (armed) {
                    delay(5_000)
                    armed = false
                }
            }
            CourseTableTheme {
                InlineDeleteAction(
                    armed = armed,
                    onArm = { armed = true },
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("确认删除").assertExists()

        composeRule.mainClock.advanceTimeBy(5_001)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("确认删除").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertExists()
    }
}
