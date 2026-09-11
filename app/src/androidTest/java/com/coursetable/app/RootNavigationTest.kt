package com.coursetable.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootTabsCanBeSelected() {
        composeRule.onNodeWithText("课程").performClick()
        composeRule.onNodeWithText("课程", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("导入").performClick()
        composeRule.onNodeWithText("课程数据导入").assertIsDisplayed()

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("课程安排、提醒、外观与数据").assertIsDisplayed()

        composeRule.onNodeWithText("课表").performClick()
        composeRule.onNodeWithText("课表", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun timetableActionOpensCourseEditorAndCanDismiss() {
        composeRule.onNodeWithContentDescription("添加课程").performClick()

        composeRule.onNodeWithText("课程名称 *").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("课程名称 *").assertDoesNotExist()
    }

    @Test
    fun centerActionWorksWithoutChangingEachRootTab() {
        val tabs = listOf("课表", "课程", "导入", "设置")
        tabs.forEach { tab ->
            composeRule.onNodeWithText(tab).performClick()
            composeRule.onNodeWithContentDescription("添加课程").performClick()
            composeRule.onNodeWithText("课程名称 *").assertIsDisplayed()
            composeRule.onNodeWithText("取消").performClick()
            composeRule.onNodeWithText("课程名称 *").assertDoesNotExist()
        }

        composeRule.onNodeWithText("课程安排、提醒、外观与数据").assertIsDisplayed()
    }

    @Test
    fun importTabShowsImportHub() {
        composeRule.onNodeWithText("导入").performClick()
        composeRule.onNodeWithText("课程数据导入").assertIsDisplayed()
        composeRule.onNodeWithText("从教务系统导入").assertIsDisplayed()
    }
}
