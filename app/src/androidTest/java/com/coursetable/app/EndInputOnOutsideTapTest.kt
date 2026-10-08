package com.coursetable.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EndInputOnOutsideTapTest {
    @get:Rule val compose = createComposeRule()
    private var imeVisible = false
    private var keyboard: SoftwareKeyboardController? = null
    private var clicks = 0
    private var trackKeyboard = false
    private var keyboardClosures = 0

    private fun showForm() {
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
            val visible = rememberImeVisible()
            val controller = LocalSoftwareKeyboardController.current
            SideEffect {
                if (trackKeyboard && imeVisible && !visible) keyboardClosures++
                imeVisible = visible
                keyboard = controller
            }
            CourseTableTheme {
                EndInputOnOutsideTap { observer ->
                    Column(observer.fillMaxSize().statusBarsPadding().imePadding()
                        .verticalScroll(rememberScrollState()).testTag("form")) {
                        var first by remember { mutableStateOf("课程") }
                        var second by remember { mutableStateOf("教师") }
                        OutlinedTextField(first, { first = it }, Modifier.fillMaxWidth().testTag("first"),
                            label = { Text("课程名称") }, singleLine = true)
                        OutlinedTextField(second, { second = it }, Modifier.fillMaxWidth().testTag("second"),
                            label = { Text("任课教师") }, singleLine = true)
                        Box(Modifier.fillMaxWidth().height(80.dp).testTag("blank"))
                        TextButton({ clicks++ }, Modifier.testTag("control")) { Text("选择星期") }
                        Spacer(Modifier.height(1200.dp))
                    }
                }
            }
        }
    }

    private fun openKeyboard(tag: String = "first") {
        compose.onNodeWithTag(tag).performTouchInput { click() }
        compose.waitUntil(5000) { imeVisible }
        compose.onNodeWithTag(tag).assertIsFocused()
    }

    @Test fun blankTapClearsFocusWithOpenOrManuallyHiddenKeyboard() {
        showForm()
        openKeyboard()
        compose.onNodeWithTag("blank").performTouchInput { click() }
        compose.onNodeWithTag("first").assertIsNotFocused()
        compose.waitUntil(5000) { !imeVisible }
        openKeyboard()
        compose.runOnIdle { keyboard?.hide() }
        compose.waitUntil(5000) { !imeVisible }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag("blank").performTouchInput { click() }
        compose.onNodeWithTag("first").assertIsNotFocused()
    }

    @Test fun controlTapEndsInputAndStillPerformsItsAction() {
        showForm()
        openKeyboard()
        compose.onNodeWithTag("control").performTouchInput { click() }
        compose.onNodeWithTag("first").assertIsNotFocused()
        compose.waitUntil(5000) { !imeVisible }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun fieldAndLabelTapsKeepEditing() {
        showForm()
        openKeyboard()
        compose.onNodeWithTag("second").performTouchInput { click(topCenter.copy(y = 8f)) }
        compose.onNodeWithTag("second").assertIsFocused().performTextInput("老师")
        compose.runOnIdle { assertTrue(imeVisible) }
        compose.onNodeWithTag("first").performTouchInput { click() }
        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test fun repeatedFieldSwitchesNeverCloseKeyboard() {
        showForm()
        openKeyboard()
        settleKeyboard()
        compose.runOnIdle { trackKeyboard = true }
        repeat(6) { index ->
            val tag = if (index % 2 == 0) "second" else "first"
            compose.onNodeWithTag(tag).performTouchInput { click() }
            settleKeyboard()
            compose.onNodeWithTag(tag).assertIsFocused()
        }
        compose.runOnIdle { assertEquals("Keyboard closed while switching fields", 0, keyboardClosures) }
    }

    private fun settleKeyboard() {
        val started = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(2000) { android.os.SystemClock.uptimeMillis() - started >= 600 }
    }

    @Test fun courseEditorSwitchesKeepKeyboardContinuouslyVisible() {
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
            val visible = rememberImeVisible()
            SideEffect {
                if (trackKeyboard && imeVisible && !visible) keyboardClosures++
                imeVisible = visible
            }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().statusBarsPadding()) {
                    CourseEditorDialog(Course(name = "数学", teacher = "老师", location = "教室103",
                        dayOfWeek = 1, startSection = 1, duration = 1, startWeek = 1, endWeek = 16),
                        16, 12, {}, {})
                }
            }
        }
        compose.onNodeWithTag("course-editor-teacher").performScrollTo()
        openKeyboard("course-editor-teacher")
        compose.onNodeWithTag("course-editor-teacher").performScrollTo()
        settleKeyboard()
        compose.runOnIdle { trackKeyboard = true }
        repeat(6) { index ->
            val tag = if (index % 2 == 0) "course-editor-location" else "course-editor-teacher"
            compose.onNodeWithTag(tag).performTouchInput { click() }
            settleKeyboard()
            compose.onNodeWithTag(tag).assertIsFocused()
        }
        compose.runOnIdle { assertEquals("Editor keyboard closed while switching fields", 0, keyboardClosures) }
    }

    @Test fun scrollAndLongPressDoNotEndInput() {
        showForm()
        openKeyboard()
        compose.onNodeWithTag("blank").performTouchInput { swipeUp() }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.runOnIdle { assertTrue(imeVisible) }
        compose.onNodeWithTag("blank").performScrollTo().performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test fun courseEditorTimeTapEndsInputAndUpdatesCourse() {
        var saved: Course? = null
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
            val visible = rememberImeVisible()
            SideEffect { imeVisible = visible }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize().statusBarsPadding()) {
                    CourseEditorDialog(Course(name = "数学", dayOfWeek = 1, startSection = 1,
                        duration = 1, startWeek = 1, endWeek = 16), 16, 12, {}, { saved = it }, embedded = true)
                }
            }
        }
        compose.onNodeWithTag("course-editor-teacher").performScrollTo()
        openKeyboard("course-editor-teacher")
        compose.onNodeWithTag("course-editor-time").performScrollTo().performTouchInput { click() }
        compose.waitUntil(5000) { !imeVisible }
        compose.onNodeWithText("周二").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("course-time-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("course-editor-teacher").assertIsNotFocused()
        compose.onNodeWithContentDescription("保存").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, saved?.dayOfWeek) }
    }
    @Test fun periodTimeSchemeNameFieldClearsFocusOnOutsideTap() {
        val currentTimes = listOf(
            com.coursetable.app.data.PeriodTime(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(8, 45))
        )
        val scheme = com.coursetable.app.data.PeriodTimeScheme("test", "测试方案", currentTimes, 45)
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
            val visible = rememberImeVisible()
            SideEffect { imeVisible = visible }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    com.coursetable.app.ui.PeriodTimeSchemesDialog(
                        schemes = listOf(scheme),
                        periods = currentTimes,
                        durationMinutes = 45,
                        onSave = {},
                        onApply = {},
                        onSaveCurrent = {},
                        onDelete = {},
                        onDismiss = {}
                    )
                }
            }
        }
        compose.onNodeWithText("测试方案").performClick()
        compose.waitForIdle()
        openKeyboard("scheme-name-input")
        compose.onNodeWithTag("scheme-name-input").assertIsFocused()

        compose.onNodeWithText("默认课时长").performTouchInput { click() }
        compose.onNodeWithTag("scheme-name-input").assertIsNotFocused()
        compose.waitUntil(5000) { !imeVisible }
    }

    @Test fun timetableNameFieldClearsFocusOnOutsideTap() {
        val tables = listOf(com.coursetable.app.data.Timetable(1L, "主课表", 16, 12, ""))
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            DisposableEffect(activity) { activity.enableEdgeToEdge(); onDispose {} }
            val visible = rememberImeVisible()
            SideEffect { imeVisible = visible }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    com.coursetable.app.ui.TimetableManageScreen(
                        timetables = tables,
                        activeId = 1L,
                        onBack = {},
                        onSwitch = {},
                        onCreate = {},
                        onRename = { _, _ -> },
                        onDelete = {}
                    )
                }
            }
        }
        compose.onNodeWithText("新建课表").performClick()
        compose.waitForIdle()
        openKeyboard("timetable-name-input")
        compose.onNodeWithTag("timetable-name-input").assertIsFocused()

        compose.onNodeWithTag("name-dialog-title", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithTag("timetable-name-input").assertIsNotFocused()
        compose.waitUntil(5000) { !imeVisible }
    }
}
