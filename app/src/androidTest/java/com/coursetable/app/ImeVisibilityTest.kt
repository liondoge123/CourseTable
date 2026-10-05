package com.coursetable.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.coursetable.app.ui.liquid.OutlinedTextField
import com.coursetable.app.ui.theme.CourseTableTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.coursetable.app.ui.liquid.rememberImeVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImeVisibilityTest {
    @get:Rule val compose = createComposeRule()

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

    @Test fun tappingAnotherFieldKeepsKeyboardOpenAndAcceptsInput() {
        var keyboardVisible = false
        var keyboard: SoftwareKeyboardController? = null
        val teacher = mutableStateOf("教师")
        val location = mutableStateOf("教室")
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) {
                activity.enableEdgeToEdge()
                onDispose {}
            }
            CourseTableTheme {
                val visible = rememberImeVisible()
                val controller = LocalSoftwareKeyboardController.current
                SideEffect { keyboardVisible = visible; keyboard = controller }
                Column {
                    OutlinedTextField(teacher.value, { teacher.value = it }, Modifier.testTag("teacher"))
                    OutlinedTextField(location.value, { location.value = it }, Modifier.testTag("location"))
                }
            }
        }
        compose.onNodeWithTag("teacher").performClick()
        compose.waitUntil(5000) { keyboardVisible }
        compose.onNodeWithTag("location").performClick()
        // Let the keyboard settle after switching the input connection.
        val started = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(2000) { android.os.SystemClock.uptimeMillis() - started > 600 }
        compose.onNodeWithTag("location").assertIsFocused().performTextInput("103")
        compose.runOnIdle { assertTrue(keyboardVisible); assertTrue(location.value.contains("103")) }
        compose.onNodeWithTag("teacher").performClick()
        compose.onNodeWithTag("teacher").assertIsFocused().performTextInput("老师")
        compose.runOnIdle { keyboard?.hide() }
        compose.waitUntil(5000) { !keyboardVisible }
        // Hiding the keyboard alone leaves the current editor focused. Tapping it
        // again must reopen the keyboard without any application focus workaround.
        compose.onNodeWithTag("teacher").assertIsFocused().performClick()
        compose.waitUntil(5000) { keyboardVisible }
        compose.onNodeWithTag("teacher").assertIsFocused().performTextInput("继续")
        compose.runOnIdle { assertTrue(teacher.value.contains("继续")) }
    }
}
