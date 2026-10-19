package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.ui.theme.ThemeMode
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseSessionsEditorTest {
    @get:Rule val compose=createComposeRule()
    private var saved: Course?=null
    private fun sample()=Course(name="通信原理",teacher="张三",location="A301",dayOfWeek=2,startSection=1,duration=2,startWeek=1,endWeek=16).also { c ->
        c.additionalSessions=listOf(c.copy(dayOfWeek=5,startSection=3,location="B301"))
    }
    private fun show(dark: Boolean=false, course: Course = sample(), fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CourseTableTheme(themeMode=if(dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)) {
                        CourseEditorDialog(course,18,12,{}, { saved=it })
                    }
                }
            }
        }
    }
    private fun choose(index: Int) {
        compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-choices", useUnmergedTree = true).performScrollToIndex(index)
        compose.onNodeWithTag("course-session-choice-$index").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("course-session-choice-$index").fetchSemanticsNodes().isEmpty()
        }
    }
    private fun evidence(name: String) {
        val session=InstrumentationRegistry.getArguments().getString("editorEvidenceSession") ?: return
        require(session.matches(Regex("[a-zA-Z0-9-]+")))
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/coursetable-testing/$session/$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    @Test fun switchingSessionsRetainsUnsavedRoomAndShowsSharedFields() {
        show()
        choose(1)
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("B301").performTextReplacement("B502")
        choose(0)
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("A301")
        choose(1)
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("B502")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitUntil(5000) { saved!=null }
        assertEquals("B502",saved!!.location)
        assertEquals("A301",saved!!.additionalSessions!!.single().location)
    }
    @Test fun addingAndRemovingASessionKeepsOtherArrangements() {
        show()
        compose.onNodeWithTag("course-session-add").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 3 / 3")
        compose.onNodeWithTag("course-session-remove").performScrollTo().performClick()
        compose.onNodeWithText("删除",substring=false).performClick()
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 1 / 2")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitUntil(5000) { saved!=null }
        assertEquals(setOf("A301","B301"),(listOf(saved!!)+saved!!.additionalSessions.orEmpty()).map { it.location }.toSet())
    }
    @Test fun managementHasNoStandaloneMergeAction() {
        compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
            CourseManageScreen(listOf(sample().copy(id=1,courseId=1)),18,12,{},{},{})
        } } }
        compose.onAllNodesWithText("合并",substring=true).assertCountEquals(0)
    }
    @Test fun lightSessionFormAndSelectionUseSharedDesign() {
        show()
        compose.onNodeWithTag("course-session-add").performScrollTo()
        evidence("editor-light")
        compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-choice-0").assertIsSelected()
        evidence("editor-selection")
    }
    @Test fun darkSessionFormRemainsReadable() {
        show(true)
        compose.onNodeWithTag("course-session-add").performScrollTo()
        compose.onNodeWithTag("course-editor-location").assertTextContains("A301")
        evidence("editor-dark")
    }

    @Test fun ordinaryTimeAndWeekRowsKeepTheirLabelsAndValuesOnOneLine() {
        show()
        compose.onNodeWithTag("course-editor-time").performScrollTo().assertHeightIsAtLeast(48.dp)
        val timeHeight = compose.onNodeWithTag("course-editor-time").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val weeksHeight = compose.onNodeWithTag("course-editor-weeks").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("Time choice should be compact at normal font size", timeHeight <= 60.dp)
        assertTrue("Week choice should be compact at normal font size", weeksHeight <= 60.dp)
        val label = compose.onNode(hasText("上课时间") and hasAnyAncestor(hasTestTag("course-editor-time")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val value = compose.onNode(hasText("周二 · 第 1–2 节") and hasAnyAncestor(hasTestTag("course-editor-time")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(label.center.y, value.center.y, 1f)
        evidence("editor-compact")
    }

    @Test fun compactSelectorDoesNotRepeatTheTimeAndCancelKeepsTheCurrentSession() {
        show()
        compose.onNodeWithTag("course-session-selector").performScrollTo()
            .assertTextContains("时段 1 / 2").assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithText("当前编辑", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("周二 · 第 1–2 节").assertCountEquals(1)
        compose.onNodeWithTag("course-session-selector").performClick()
        compose.onNodeWithTag("course-session-choice-0").assertIsSelected()
        compose.onNodeWithTag("course-session-choice-1").assertIsNotSelected()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 1 / 2")
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("A301")
    }

    @Test fun reopeningManySessionsShowsTheCurrentArrangement() {
        val course = sample().also { first ->
            first.additionalSessions = (1..11).map { index ->
                first.copy(dayOfWeek = index % 7 + 1, startSection = index % 12 + 1, duration = 1, location = "教室 $index")
            }
        }
        show(course = course)
        compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-choices", useUnmergedTree = true).performScrollToIndex(11)
        compose.onNodeWithTag("course-session-choice-11").assertIsDisplayed()
        evidence("editor-before-long-choice")
        compose.onNodeWithTag("course-session-choice-11").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("course-session-choice-11").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 12 / 12").performClick()
        compose.onNodeWithTag("course-session-choice-11").assertIsSelected().assertIsDisplayed()
        evidence("editor-current-in-long-list")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("教室 11")
    }

    @Test fun removingBackToOneSessionHidesTheSwitcher() {
        show(course = sample().copy().also { it.additionalSessions = null })
        compose.onNodeWithTag("course-session-selector").assertDoesNotExist()
        compose.onNodeWithTag("course-session-add").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 2 / 2")
        compose.onNodeWithTag("course-session-remove").performScrollTo().performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithTag("course-session-selector").assertDoesNotExist()
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("A301")
    }

    private fun largeFontSelection(dark: Boolean) {
        val course = sample().also { first ->
            first.additionalSessions = listOf(first.copy(dayOfWeek = 5, startSection = 9, duration = 1,
                location = "第二实验教学楼 B502（东校区综合实验室）"))
        }
        show(dark, course, fontScale = 1.5f)
        compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
        compose.onNodeWithText("周五 · 第 9 节").assertIsDisplayed()
        compose.onNodeWithText("第二实验教学楼 B502（东校区综合实验室）", substring = true).performScrollTo().assertIsDisplayed()
        evidence(if (dark) "editor-selection-large-dark" else "editor-selection-large-light")
        compose.onNodeWithTag("course-session-choice-1").performClick()
        compose.onNodeWithTag("course-session-selector").assertTextContains("时段 2 / 2")
    }
    @Test fun lightSelectorSupportsLargeTextAndLongLocations() = largeFontSelection(false)
    @Test fun darkSelectorSupportsLargeTextAndLongLocations() = largeFontSelection(true)
}
