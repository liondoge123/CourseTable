package com.coursetable.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun swipeBack(cancel: Boolean, duringGesture: () -> Unit = {}) {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 300f, 0.65f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
        duringGesture()
        composeRule.runOnIdle {
            if (cancel) composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            else composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test fun settingsSubpageKeepsItsParentAndSupportsCancelledBack() {
        composeRule.onAllNodesWithText("设置")[0].performClick()
        val parentPixels = composeRule.onRoot().captureToImage().toPixelMap()
        val statusBarBackground = parentPixels[parentPixels.width / 2, 1].luminance()
        composeRule.onNodeWithText("课表管理").performClick()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        val page = composeRule.onNodeWithTag("timetable-manage-page").getUnclippedBoundsInRoot()
        // Safe insets belong to the content, not outside the full-window animated page.
        assertEquals(window.top.value, page.top.value, 0.5f)
        assertEquals(window.bottom.value, page.bottom.value, 0.5f)
        swipeBack(cancel = true) {
            val movingPage = composeRule.onNodeWithTag("timetable-manage-page").getUnclippedBoundsInRoot()
            assertTrue("Top of the full page must move with the gesture", movingPage.top > window.top)
            val previewPixels = composeRule.onRoot().captureToImage().toPixelMap()
            assertTrue("The scrim must also dim the retained parent's status bar background",
                previewPixels[previewPixels.width / 2, 1].luminance() < statusBarBackground)
        }
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        swipeBack(cancel = false)
        composeRule.onNodeWithText("课程安排、提醒、外观与数据").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test fun eduImportReturnsToRetainedImportHub() {
        composeRule.onAllNodesWithText("导入")[0].performClick()
        composeRule.onNodeWithText("从教务系统导入").performClick()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        swipeBack(cancel = true)
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        swipeBack(cancel = false)
        composeRule.onNodeWithText("课程数据导入").assertIsDisplayed()
    }

    @Test
    fun rootTabsCanBeSelected() {
        composeRule.onAllNodesWithText("课程")[0].performClick()
        composeRule.onAllNodesWithText("课程", useUnmergedTree = true)[0].assertIsDisplayed()

        composeRule.onAllNodesWithText("导入")[0].performClick()
        composeRule.onNodeWithText("课程数据导入").assertIsDisplayed()

        composeRule.onAllNodesWithText("设置")[0].performClick()
        composeRule.onNodeWithText("课程安排、提醒、外观与数据").assertIsDisplayed()

        composeRule.onAllNodesWithText("课表")[0].performClick()
        composeRule.onAllNodesWithText("课表", useUnmergedTree = true)[0].assertIsDisplayed()
    }

    @Test
    fun timetableActionOpensCourseEditorAndCanDismiss() {
        composeRule.onNodeWithContentDescription("添加课程").performClick()

        composeRule.onNodeWithText("课程名称 *").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNodeWithText("课程名称 *").assertDoesNotExist()
    }

    @Test
    fun actionButtonAppearsOnTimetableAndCoursesOnly() {
        // Timetable tab: action button is visible and functional
        composeRule.onAllNodesWithText("课表")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加课程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加课程").performClick()
        composeRule.onNodeWithText("课程名称 *").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNodeWithText("课程名称 *").assertDoesNotExist()

        // Courses tab: action button is visible and functional
        composeRule.onAllNodesWithText("课程")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加课程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加课程").performClick()
        composeRule.onNodeWithText("课程名称 *").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消").performClick()
        composeRule.onNodeWithText("课程名称 *").assertDoesNotExist()

        // Import tab: action button disappears
        composeRule.onAllNodesWithText("导入")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加课程").assertDoesNotExist()

        // Settings tab: action button disappears
        composeRule.onAllNodesWithText("设置")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加课程").assertDoesNotExist()
    }

    @Test
    fun importTabShowsImportHub() {
        composeRule.onAllNodesWithText("导入")[0].performClick()
        composeRule.onNodeWithText("课程数据导入").assertIsDisplayed()
        composeRule.onNodeWithText("从教务系统导入").assertIsDisplayed()
    }

    @Test
    fun aboutSubpageDisplaysOpenSourceAndLicenseDetailsAndReturnsToSettings() {
        composeRule.onAllNodesWithText("设置")[0].performClick()
        // Scroll-to only exposes the row at the viewport edge, where the floating
        // dock can cover its touch center. Invoke the row action for this navigation test.
        composeRule.onNodeWithText("关于 CourseTable").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag("about-page").isDisplayed()
        }
        composeRule.onNodeWithTag("about-page").assertIsDisplayed()
        composeRule.onNodeWithText("关于 CourseTable").assertIsDisplayed()
        composeRule.onNodeWithText("Apache License 2.0").assertIsDisplayed()
        composeRule.onNodeWithText("免责与责任限制声明").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("课程安排、提醒、外观与数据").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("about-page").assertDoesNotExist()
    }
}
