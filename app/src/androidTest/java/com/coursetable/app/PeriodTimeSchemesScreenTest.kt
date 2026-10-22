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
import com.coursetable.app.ui.PeriodTimeSchemesScreen
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PeriodTimeSchemesScreenTest {
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

    private fun show(initial: List<PeriodTimeScheme> = listOf(winter), failSave: Boolean = false, customTime: Boolean = false) {
        compose.setContent {
            var schemes by remember { mutableStateOf(PeriodTimeScheme.withDefault(initial, PeriodTimeScheme.defaultFor(0, current, 45))) }
            var times by remember { mutableStateOf(current) }
            var activeSchemeId by remember { mutableStateOf<String?>(if (customTime) "custom" else null) }
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    PeriodTimeSchemesScreen(
                        schemes = schemes, periods = times, durationMinutes = 45,
                        currentSchemeId = activeSchemeId,
                        onSave = { item ->
                            if (failSave) error("Disk unavailable")
                            saved = item
                            schemes = schemes.filterNot { it.id == item.id } + item
                        },
                        onApply = { applied = it; times = it.periods; activeSchemeId = it.id },
                        onSaveCurrent = { savedCurrent = it; times = it.periods; activeSchemeId = it.id; if (it.isDefault) schemes = schemes.filterNot { item -> item.id == it.id } + it },
                        onDelete = { id -> schemes = schemes.filterNot { it.id == id }; if (activeSchemeId == id) activeSchemeId = null },
                        onDismiss = {}
                    )
                }
            }
        }
    }

    private fun editScheme(name: String) {
        compose.onNodeWithContentDescription("方案「$name」的更多操作").performClick()
        compose.onNodeWithText("编辑方案").performClick()
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$").matches(directory))
        compose.onRoot().captureToImage()
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p $directory/$name.png")
        ).use { it.readBytes() }
    }

    @Test fun rowSelectsWithoutEditingAndMenuDoesNotChangeSelection() {
        show()
        val before = compose.onNodeWithTag("scheme-row-winter").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("scheme-select-winter").assertIsNotSelected().performClick().assertIsSelected()
        val after = compose.onNodeWithTag("scheme-row-winter").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        assertEquals(after, compose.onNodeWithTag("scheme-select-winter").getUnclippedBoundsInRoot())
        compose.onNodeWithText("已选用").assertDoesNotExist()
        compose.onNodeWithTag("scheme-name-input").assertDoesNotExist()
        compose.runOnIdle { assertEquals(winter, applied); applied = null }
        compose.onNodeWithTag("scheme-select-winter").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scheme-select-winter").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        capture("scheme-full-row-pressed")
        compose.onNodeWithTag("scheme-select-winter").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("选用").assertDoesNotExist()
        capture("scheme-selected")
        compose.onNodeWithContentDescription("方案「默认方案」的更多操作").performClick()
        compose.onNodeWithText("删除方案").assertDoesNotExist()
        capture("scheme-default-menu")
        compose.onNodeWithText("编辑方案").performClick()
        compose.onNodeWithContentDescription("加 5 分钟").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertNull(applied); assertNull(savedCurrent); assertEquals(50, saved?.durationMinutes) }
        compose.onNodeWithTag("scheme-select-winter").assertIsSelected()
    }

    @Test fun pageListEditsAndAppliesWithoutClosingThePage() {
        show()
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
        editScheme("冬季作息")
        compose.onNodeWithTag("scheme-name-input").performTextReplacement("冬季时间")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
        compose.onNodeWithText("冬季时间").assertIsDisplayed()
        compose.onNodeWithText("冬季时间").performClick()
        compose.runOnIdle { assertEquals("冬季时间", applied?.name) }
        compose.onNodeWithTag("scheme-select-winter").assertIsSelected()
        editScheme("冬季时间")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
    }

    @Test fun editorOpensAboveTheRetainedPageAndReturnsToIt() {
        show()
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
        editScheme("冬季作息")
        // The modal editor hides the retained page from interaction/accessibility.
        compose.onNodeWithTag("period-times-page", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("overlay-glass-dialog").assertCountEquals(1)
        compose.onNodeWithTag("scheme-name-input").assertIsDisplayed()
        capture("scheme-editor-over-page")
        compose.onNodeWithContentDescription("返回方案列表").performClick()
        compose.onNodeWithTag("scheme-name-input").assertDoesNotExist()
        compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        capture("scheme-list-page-restored")
    }

    @Test fun editingSchemeDoesNotApplyIt() {
        show()
        editScheme("冬季作息")
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
        compose.onNodeWithText("冬季作息").performClick()
        compose.onNodeWithTag("scheme-select-winter").assertIsSelected()
        editScheme("冬季作息")
        compose.onNodeWithText("默认课时长").assertIsDisplayed()
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
        ).use { it.readBytes() }
        compose.onNodeWithTag("scheme-select-winter").assertIsSelected()
        compose.onNodeWithText("默认课时长").assertDoesNotExist()
        compose.runOnIdle { assertEquals(winter, applied); assertNull(saved) }
    }

    @Test fun failedSavePreservesNameAndEditor() {
        show(failSave = true)
        editScheme("冬季作息")
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
        compose.onNodeWithText("2 节 · 08:00–09:50").assertIsDisplayed()
        compose.onNodeWithContentDescription("新建方案").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("夏季作息")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("夏季作息").assertIsDisplayed()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.runOnIdle {
            assertEquals(current, saved?.periods)
            assertNotNull(saved)
            assertNull(applied)
        }
    }

    @Test fun toolbarCreationStaysReachableAtTheEndOfALongSchemeList() {
        show(initial = (1..30).map { winter.copy(id = "scheme-$it", name = "作息方案 $it") })
        compose.onNodeWithTag("scheme-row-scheme-30").performScrollTo()
        compose.onNodeWithContentDescription("新建方案").assertIsDisplayed().performClick()
        compose.onNodeWithTag("scheme-name-input").performTextReplacement("新增作息")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("新增作息", saved?.name)
            assertEquals(current, saved?.periods)
            assertNull(applied)
        }
        compose.onNodeWithTag("period-times-page").assertIsDisplayed()
        capture("scheme-toolbar-after-long-list")
    }

    @Test fun savedAsSchemeFromDefaultDoesNotDuplicateSelectedStateAndCanBeApplied() {
        show(initial = emptyList())
        editScheme("默认方案")
        compose.onNodeWithText("另存为方案").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("自建方案")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("自建方案").assertIsDisplayed()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithText("自建方案").performClick()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.runOnIdle {
            assertEquals("自建方案", applied?.name)
        }
    }

    @Test fun customTimeSaveUsesCurrentTableCallback() {
        show(customTime = true)
        compose.onNodeWithContentDescription("当前自定义的更多操作").performClick()
        compose.onNodeWithText("编辑当前时间").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithContentDescription("新建方案").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(current, savedCurrent?.periods)
            assertNull(saved)
            assertNull(applied)
        }
    }

    @Test fun customTimeCanBeSavedAsSchemeAndDeletedWithConfirmation() {
        show(initial = emptyList())
        editScheme("默认方案")
        compose.onNodeWithText("另存为方案").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("自建方案")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(current, saved?.periods); assertNull(savedCurrent) }
        compose.onNodeWithContentDescription("方案「自建方案」的更多操作").performClick()
        compose.onNodeWithText("删除方案").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("自建方案").assertIsDisplayed()
        compose.onNodeWithContentDescription("方案「自建方案」的更多操作").performClick()
        compose.onNodeWithText("删除方案").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.onNodeWithText("默认方案").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除默认方案").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied); assertNull(savedCurrent) }
    }

    @Test fun sectionChangesAndTimePickerSaveIntoSchemeOnly() {
        show()
        editScheme("冬季作息")
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
        editScheme("默认方案")
        compose.onNodeWithContentDescription("加 5 分钟").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(50, saved?.durationMinutes)
            assertEquals(current, saved?.periods)
            assertNull(savedCurrent)
            assertNull(applied)
        }
    }

    @Test fun swipingTheDefaultSchemeDoesNotOfferDeletion() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("方案「默认方案」的更多操作").performClick()
        compose.onNodeWithText("删除方案").assertDoesNotExist()
        compose.onNodeWithText("编辑方案").assertIsDisplayed()
    }

    @Test fun emptyCustomListHasSelectedDefaultWithoutDeleteOrRename() {
        show(initial = emptyList())
        compose.onNodeWithText("默认方案").assertIsDisplayed()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithContentDescription("删除默认方案").assertDoesNotExist()
        compose.onNodeWithText("暂无方案").assertDoesNotExist()
        editScheme("默认方案")
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("默认方案").assertIsDisplayed()
    }

    @Test fun bulkDurationPreviewRequiresExplicitApplyAndDoesNotSelectScheme() {
        show()
        editScheme("冬季作息")
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
