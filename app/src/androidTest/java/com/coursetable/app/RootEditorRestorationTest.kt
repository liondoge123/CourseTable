package com.coursetable.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test

class RootEditorRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun replaceText(tag: String, value: String) {
        // Restoration uses the Compose clock. NativeKeyboardSwitchTest separately
        // exercises real keyboard timing with native input and the render clock.
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetText) {
            it(AnnotatedString(value))
        }
    }

    @Test fun activityRecreationRetainsRootEditorAndEveryUnsavedSession() {
        compose.onNodeWithContentDescription("添加课程").performClick()
        replaceText("course-editor-name", "重建中的新课")
        replaceText("course-editor-location", "第一教室")
        compose.onNodeWithTag("course-session-add").performScrollTo().performClick()
        replaceText("course-editor-location", "第二教室")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("course-editor-name").performScrollTo().assertTextContains("重建中的新课")
        compose.onNodeWithTag("course-session-selector").performScrollTo().assertTextContains("时段 2 / 2")
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("第二教室")
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
    }
}
