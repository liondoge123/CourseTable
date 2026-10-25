package com.coursetable.app

import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCheckNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openPermissions() {
        compose.onAllNodesWithText("设置")[0].performClick()
        compose.onNodeWithText("权限检查").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("permission-check-page").assertIsDisplayed()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
    }

    @Test fun permissionsUseASubpageAndKeepTheSettingsScrollPosition() {
        openPermissions()
        compose.onNodeWithText("通知权限").assertIsDisplayed()
        compose.onNodeWithText("横幅通知").assertIsDisplayed()
        compose.onNodeWithText("精确闹钟").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("需手动确认").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("发送测试提醒").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("permission-check-page").assertDoesNotExist()
        compose.onNodeWithText("权限检查").assertIsDisplayed()
        compose.onNodeWithText("发送测试提醒").assertDoesNotExist()
    }

    @Test fun permissionsSurviveRecreationAndSupportCancelledSystemBack() {
        openPermissions()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("permission-check-page").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)
            )
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(150f, 300f, .65f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithTag("permission-check-page").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("permission-check-page").assertDoesNotExist()
        compose.onNodeWithText("权限检查").assertIsDisplayed()
    }
}
