package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsOrganizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openSettings() {
        compose.onAllNodesWithText("设置")[0].performClick()
        compose.onNodeWithTag("settings-scroll").assertIsDisplayed()
    }

    private fun scrollPosition() = compose.onNodeWithTag("settings-scroll").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun overviewKeepsDirectControlsAndMovesDetailedChoicesToTheirPages() {
        openSettings()
        for (title in listOf("学期开始日期", "学期总周数", "显示非本周课程", "课前提醒", "主题模式")) {
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        for (title in listOf("显示教师", "教师与地点顺序", "强调色", "玻璃效果", "清空所有课程")) {
            compose.onNodeWithText(title).assertDoesNotExist()
        }
        capture("settings-overview")
        compose.onNodeWithText("课程卡片设置").performScrollTo().performClick()
        compose.onNodeWithTag("course-display-page").assertIsDisplayed()
        compose.onNodeWithText("显示教师").assertIsDisplayed()
        compose.onNodeWithText("教师与地点顺序").assertIsDisplayed()
        capture("course-display-page")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("显示非本周课程").assertIsDisplayed()
        compose.onNodeWithText("主题模式").performScrollTo().performClick()
        compose.onNodeWithTag("theme-mode-auto").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings-scroll").assertIsDisplayed()
    }

    @Test fun wallpaperReturnsThroughAppearanceAndRestoresOverviewScroll() {
        openSettings()
        compose.onNodeWithText("外观设置").performScrollTo()
        val before = scrollPosition()
        val app = compose.activity.application as CourseApp
        val wallpaper = runBlocking { app.settingsRepository.wallpaper.first() }
        compose.onNodeWithText("外观设置").performClick()
        compose.onNodeWithTag("appearance-settings-page").assertIsDisplayed()
        compose.onNodeWithText("强调色").assertIsDisplayed()
        capture("appearance-settings-page")
        compose.onNodeWithText("课表壁纸").performScrollTo().performClick()
        compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(180f, 300f, .6f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("wallpaper-editor").assertDoesNotExist()
        compose.onNodeWithTag("appearance-settings-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("appearance-settings-page").assertDoesNotExist()
        assertEquals(before, scrollPosition(), 1f)
        assertEquals(wallpaper, runBlocking { app.settingsRepository.wallpaper.first() })
    }

    @Test fun backupPageKeepsDestructiveActionBehindItsExistingConfirmation() {
        openSettings()
        compose.onNodeWithText("备份与分享").performScrollTo()
        val before = scrollPosition()
        compose.onNodeWithText("备份与分享").performClick()
        compose.onNodeWithTag("data-settings-page").assertIsDisplayed()
        compose.onNodeWithText("复制当前课表分享文本").assertIsDisplayed()
        compose.onNodeWithText("导出备份文件").assertIsDisplayed()
        compose.onNodeWithText("导出 ICS 日历").assertIsDisplayed()
        capture("data-settings-page")
        compose.onNodeWithText("清空所有课程").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("data-settings-page").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("data-settings-page").assertDoesNotExist()
        assertEquals(before, scrollPosition(), 1f)
    }

    @Test fun reminderStatusOpensDetailsAndReturnsToTheSameOverviewPosition() {
        openSettings()
        compose.onNodeWithText("排程状态").performScrollTo()
        val before = scrollPosition()
        compose.onNodeWithText("触发日志").assertDoesNotExist()
        compose.onNodeWithText("下次提醒").assertDoesNotExist()
        compose.onNodeWithText("排程状态").performClick()
        compose.onNodeWithTag("reminder-status-page").assertIsDisplayed()
        compose.onNodeWithText("已排程提醒").assertIsDisplayed()
        compose.onNodeWithText("下次提醒").assertIsDisplayed()
        compose.onNodeWithText("触发日志").performScrollTo().assertIsDisplayed()
        capture("reminder-status-page")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reminder-status-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("reminder-status-page").assertDoesNotExist()
        compose.onNodeWithText("排程状态").assertIsDisplayed()
        compose.onNodeWithText("触发日志").assertDoesNotExist()
        assertEquals(before, scrollPosition(), 1f)
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(directory.matches(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$")))
        compose.waitForIdle()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $directory/$name.png")).use { it.readBytes() }
    }
}
