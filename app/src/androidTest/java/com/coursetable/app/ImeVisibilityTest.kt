package com.coursetable.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.coursetable.app.ui.liquid.rememberImeVisible
import com.coursetable.app.ui.liquid.ClearFocusOnImeDismiss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImeVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardDismissClearsFocusAndAllowsRefocusing() {
        val visible = mutableStateOf(false)
        val requester = FocusRequester()
        compose.setContent {
            ClearFocusOnImeDismiss(visible.value)
            BasicTextField(
                value = "方案名称", onValueChange = {},
                modifier = Modifier.focusRequester(requester).testTag("name-field")
            )
        }
        compose.runOnIdle { requester.requestFocus() }
        compose.onNodeWithTag("name-field").assertIsFocused()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("name-field").assertIsFocused()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("name-field").assertIsNotFocused()
        compose.runOnIdle { requester.requestFocus() }
        compose.onNodeWithTag("name-field").assertIsFocused()
    }

    @Test fun intermediateKeyboardHeightsDoNotRecomposeVisibilityConsumer() {
        val height = mutableIntStateOf(0)
        val insets = object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getTop(density: Density) = 0
            override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getBottom(density: Density) = height.intValue
        }
        var commits = 0
        var lastVisible = false
        compose.setContent {
            val visible = rememberImeVisible(insets)
            SideEffect { commits++; lastVisible = visible }
            BasicText(if (visible) "keyboard visible" else "keyboard hidden")
        }
        compose.runOnIdle { assertFalse(lastVisible); height.intValue = 1 }
        compose.waitForIdle()
        val openCommits = commits
        compose.runOnIdle { assertTrue(lastVisible) }
        for (value in listOf(25, 80, 160, 320, 240, 120, 10)) {
            compose.runOnIdle { height.intValue = value }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(openCommits, commits); assertTrue(lastVisible) }
        }
        compose.runOnIdle { height.intValue = 0 }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(lastVisible); assertEquals(openCommits + 1, commits) }
    }
}
