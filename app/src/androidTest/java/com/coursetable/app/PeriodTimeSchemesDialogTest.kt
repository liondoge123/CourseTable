package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodTimeScheme
import com.coursetable.app.ui.PeriodTimeSchemesDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PeriodTimeSchemesDialogTest {
    @get:Rule val compose = createComposeRule()
    private val current = listOf(
        PeriodTime(LocalTime.of(8, 0), LocalTime.of(8, 45)),
        PeriodTime(LocalTime.of(9, 0), LocalTime.of(9, 50))
    )
    private val winter = PeriodTimeScheme(
        "winter", "冬季作息",
        listOf(PeriodTime(LocalTime.of(8, 30), LocalTime.of(9, 15))), 45
    )
    private var saved: PeriodTimeScheme? = null
    private var applied: PeriodTimeScheme? = null
    private var savedCurrent: PeriodTimeScheme? = null

    private fun show(initial: List<PeriodTimeScheme> = listOf(winter), failSave: Boolean = false) {
        compose.setContent {
            var schemes by remember { mutableStateOf(PeriodTimeScheme.withDefault(initial, PeriodTimeScheme.defaultFor(0, current, 45))) }
            var times by remember { mutableStateOf(current) }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    PeriodTimeSchemesDialog(
                        schemes = schemes, periods = times, durationMinutes = 45,
                        onSave = { item ->
                            if (failSave) error("Disk unavailable")
                            saved = item
                            schemes = schemes.filterNot { it.id == item.id } + item
                        },
                        onApply = { applied = it; times = it.periods },
                        onSaveCurrent = { savedCurrent = it; times = it.periods; if (it.isDefault) schemes = schemes.filterNot { item -> item.id == it.id } + it },
                        onDelete = { id -> schemes = schemes.filterNot { it.id == id } },
                        onDismiss = {}
                    )
                }
            }
        }
    }

    @Test fun listAndEditorKeepHorizontalAlignmentWithinOneGlassFrame() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("冬季作息").performClick()
        compose.mainClock.advanceTimeBy(90)
        compose.onAllNodesWithTag("overlay-glass-dialog").assertCountEquals(1)
        compose.onNodeWithText("编辑方案").assertExists()
        val enteringTitle = compose.onNodeWithText("编辑方案").getUnclippedBoundsInRoot()

        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText("节次时间").assertDoesNotExist()
        compose.onNodeWithText("编辑方案").assertIsDisplayed()
        val settledTitle = compose.onNodeWithText("编辑方案").getUnclippedBoundsInRoot()
        assertEquals(enteringTitle.left, settledTitle.left)
        assertEquals(enteringTitle.right, settledTitle.right)

        compose.onNodeWithContentDescription("返回方案列表").performClick()
        compose.mainClock.advanceTimeBy(90)
        compose.onAllNodesWithTag("overlay-glass-dialog").assertCountEquals(1)
        compose.onNodeWithText("节次时间").assertExists()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText("编辑方案").assertDoesNotExist()
        compose.onNodeWithText("节次时间").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun editingSchemeDoesNotApplyIt() {
        show()
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("冬季时间")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("冬季时间").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("冬季时间", saved?.name)
            assertEquals(winter.periods, saved?.periods)
            assertNull(applied)
        }
    }

    @Test fun selectingSchemeAndReturningFromDetailsKeepsListOpen() {
        show()
        compose.onNodeWithText("选用").performClick()
        compose.onNodeWithText("已选用").assertIsDisplayed()
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNodeWithText("默认课时长").assertIsDisplayed()
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
        ).use { it.readBytes() }
        compose.onNodeWithText("已选用").assertIsDisplayed()
        compose.onNodeWithText("默认课时长").assertDoesNotExist()
        compose.runOnIdle { assertEquals(winter, applied); assertNull(saved) }
    }

    @Test fun failedSavePreservesNameAndEditor() {
        show(failSave = true)
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("未保存的修改")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存失败，请重试").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("未保存的修改")
        compose.onNodeWithText("默认课时长").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("冬季作息").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved); assertNull(applied) }
    }

    @Test fun newSchemeCopiesCurrentTimesWithoutApplyingOrLosingIndividualEnds() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").assertIsDisplayed()
        compose.onNodeWithText("新建方案").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("夏季作息")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("夏季作息").assertIsDisplayed()
        compose.onAllNodesWithText("已选用").assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(current, saved?.periods)
            assertNotNull(saved)
            assertNull(applied)
        }
    }

    @Test fun customTimeSaveUsesCurrentTableCallback() {
        show()
        compose.onNodeWithText("默认方案").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("新建方案").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(current, savedCurrent?.periods)
            assertNull(saved)
            assertNull(applied)
        }
    }

    @Test fun customTimeCanBeSavedAsSchemeAndDeletedWithConfirmation() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").performClick()
        compose.onNodeWithText("另存为方案").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("自建方案")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(current, saved?.periods); assertNull(savedCurrent) }
        compose.onNodeWithContentDescription("删除自建方案").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("自建方案").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除自建方案").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.onNodeWithText("默认方案").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除默认方案").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied); assertNull(savedCurrent) }
    }

    @Test fun sectionChangesAndTimePickerSaveIntoSchemeOnly() {
        show()
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNodeWithContentDescription("加 5 分钟").performClick()
        compose.onNodeWithText("添加节次").performScrollTo().performClick()
        compose.onNodeWithContentDescription("删除第 1 节").performScrollTo().performClick()
        compose.onNodeWithTag("period-row-1").performClick()
        compose.onNodeWithText("第 1 节课").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(50, saved?.durationMinutes)
            assertEquals(listOf(PeriodTime(LocalTime.of(9, 25), LocalTime.of(10, 15))), saved?.periods)
            assertNull(applied)
        }
    }

    @Test fun changingDefaultPreservesExistingCustomEnds() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").performClick()
        compose.onNodeWithContentDescription("加 5 分钟").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(50, savedCurrent?.durationMinutes)
            assertEquals(current, savedCurrent?.periods)
        }
    }

    @Test fun emptyCustomListHasSelectedDefaultWithoutDeleteOrRename() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").assertIsDisplayed()
        compose.onNodeWithText("已选用").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除默认方案").assertDoesNotExist()
        compose.onNodeWithText("暂无方案").assertDoesNotExist()
        compose.onNodeWithText("默认方案").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("默认方案").assertIsDisplayed()
    }

    @Test fun bulkDurationPreviewRequiresExplicitApplyAndDoesNotSelectScheme() {
        show()
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNodeWithContentDescription("加 5 分钟").performClick()
        compose.onNodeWithText("统一已有节次课时长…").performClick()
        compose.onNodeWithText("调整预览").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("统一已有节次课时长…").performClick()
        compose.onNodeWithText("应用到草稿").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(LocalTime.of(9, 20), saved?.periods?.single()?.end)
            assertNull(applied)
        }
    }
}
