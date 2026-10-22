package com.coursetable.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.TimetableManageScreen
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManageScreenDetailsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        mode: State<ThemeMode> = mutableStateOf(ThemeMode.LIGHT),
        fontScale: Float = 1f,
        narrow: Boolean = false,
        content: @Composable () -> Unit
    ) {
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            SideEffect { activity.enableEdgeToEdge() }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CourseTableTheme(themeMode = mode.value) {
                    Box(if (narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        LiquidBackdropHost(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$").matches(directory))
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $directory/$name.png").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
    }

    @Test fun courseSessionsRemainReadableWithLongTextAndLargeFontInBothThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val first = Course(
            id = 1, courseId = 10, name = "模拟电子技术基础与综合实验", teacher = "张老师、李老师",
            location = "第一教学楼 A301（东校区）", dayOfWeek = 1, startSection = 1, duration = 2,
            startWeek = 1, endWeek = 16
        )
        val courses = listOf(first, first.copy(id = 2, dayOfWeek = 5, startSection = 9, duration = 1, location = "实验楼 B502"))
        show(mode, fontScale = 1.5f, narrow = true) {
            CourseManageScreen(courses, 18, 12, {}, {}, {}, rootMode = true, bottomContentPadding = 88.dp)
        }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode.value = theme }
            compose.onNodeWithText(first.name).assertIsDisplayed()
            compose.onAllNodesWithText("教师：张老师、李老师").assertCountEquals(1)
            compose.onNodeWithText("2 个上课时段").assertIsDisplayed()
            compose.onNodeWithText("周一 · 第1–2节").assertIsDisplayed()
            compose.onNodeWithText("周五 · 第9节").assertIsDisplayed()
            compose.onNodeWithText("实验楼 B502", substring = true).assertIsDisplayed()
            val title = compose.onNodeWithText(first.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val row = compose.onNodeWithTag("managed-course-${first.id}").fetchSemanticsNode().boundsInRoot
            assertTrue("The title must remain inside the course row", title.right <= row.right)
            compose.onNodeWithContentDescription("课程「${first.name}」的更多操作").assertDoesNotExist()
            compose.onNodeWithContentDescription("删除").assertDoesNotExist()
            capture("courses-large-${theme.key}")
        }
    }

    @Test fun emptyCourseListCanOpenTheEditorDirectly() {
        show {
            CourseManageScreen(emptyList(), 18, 12, {}, {}, {}, rootMode = true, bottomContentPadding = 88.dp)
        }
        compose.onNodeWithText("还没有课程").assertIsDisplayed()
        capture("courses-empty")
        compose.onNodeWithText("添加课程").performClick()
        compose.onNodeWithTag("course-editor-name").assertExists()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithText("还没有课程").assertIsDisplayed()
    }

    @Test fun ordinaryCourseRowsStayCompactWithoutLosingTheirArrangements() {
        val math = Course(id = 1, name = "高等数学", teacher = "张老师", location = "A301",
            dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16)
        val english = math.copy(id = 2, name = "大学英语", teacher = "", startSection = 3, location = "B302")
        val lab = math.copy(id = 3, courseId = 10, name = "物理实验", startSection = 5)
        val second = lab.copy(id = 4, dayOfWeek = 5, startSection = 9, location = "B502")
        show { CourseManageScreen(listOf(math, english, lab, second), 18, 12, {}, {}, {}, rootMode = true) }
        val mathHeight = compose.onNodeWithTag("managed-course-1").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val englishHeight = compose.onNodeWithTag("managed-course-2").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val labHeight = compose.onNodeWithTag("managed-course-3").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("A normal course with a teacher should fit in a compact row", mathHeight <= 96.dp)
        assertTrue("A course without extra metadata should stay short", englishHeight <= 80.dp)
        assertTrue("Two arrangements should remain a concise summary", labHeight <= 128.dp)
        compose.onAllNodesWithText("教师：张老师").assertCountEquals(2)
        compose.onNodeWithText("周五 · 第9–10节").assertIsDisplayed()
        compose.onNodeWithText("B502", substring = true).assertIsDisplayed()
        capture("courses-compact")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "Compact heights: teacher=${mathHeight.value}dp, basic=${englishHeight.value}dp, two-sessions=${labHeight.value}dp\n")
        })
    }

    @Test fun timetableSelectionKeepsItsSizeAndFeedbackCoversTheWholeRow() {
        val activeId = mutableStateOf(1L)
        val tables = listOf(Timetable(1, "主课表"), Timetable(2, "实验课"))
        show { TimetableManageScreen(tables, activeId.value, {}, { activeId.value = it }, {}, { _, _ -> }, {}) }
        compose.onNodeWithText("当前课表").assertDoesNotExist()
        val before = compose.onNodeWithTag("timetable-card-2").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("timetable-select-2").performClick().assertIsSelected()
        val after = compose.onNodeWithTag("timetable-card-2").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        assertEquals(after, compose.onNodeWithTag("timetable-select-2").getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("timetable-select-2").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        capture("timetable-full-row-pressed")
        compose.onNodeWithTag("timetable-select-2").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("课表「实验课」的更多操作").performClick()
        compose.onNodeWithText("重命名").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2L, activeId.value) }
    }

    @Test fun timetableSelectionAndActionsStaySeparateAtLargeFont() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val tables = listOf(Timetable(1, "2026–2027 学年第一学期 · 计算机科学与技术专业"), Timetable(2, "实验课与选修课"))
        val activeId = mutableStateOf(1L)
        var switches = 0
        var renamed: Pair<Long, String>? = null
        var deleted: Timetable? = null
        show(mode, fontScale = 1.5f, narrow = true) {
            TimetableManageScreen(tables, activeId.value, {}, { activeId.value = it; switches++ }, {},
                { id, name -> renamed = id to name }, { deleted = it })
        }
        compose.onNodeWithTag("timetable-select-1").assertIsSelected()
        compose.onNodeWithTag("timetable-select-2").assertIsNotSelected()
        capture("timetables-large-light")
        compose.runOnIdle { mode.value = ThemeMode.DARK }
        capture("timetables-large-dark")
        compose.onNodeWithContentDescription("课表「${tables[0].name}」的更多操作").performClick()
        compose.onNodeWithText("重命名").performClick()
        compose.onNodeWithTag("timetable-name-input").performTextReplacement("重命名后的课表")
        compose.onNodeWithText("确定").performClick()
        // Confirmation commits after the dialog's exit, beyond the button's click frame.
        compose.waitUntil(timeoutMillis = 3000) { renamed != null }
        compose.runOnIdle {
            assertEquals(1L to "重命名后的课表", renamed)
            assertEquals(0, switches)
        }
        compose.onNodeWithTag("timetables-list").performScrollToNode(hasTestTag("timetable-select-2"))
        compose.onNodeWithTag("timetable-select-2").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("课表「${tables[1].name}」的更多操作").performScrollTo().performClick()
        compose.onNodeWithText("删除课表").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertNull(deleted); assertEquals(1, switches) }
        compose.onNodeWithContentDescription("课表「${tables[1].name}」的更多操作").performClick()
        compose.onNodeWithText("删除课表").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(tables[1], deleted); assertEquals(1, switches) }
    }

    @Test fun onlyTimetableCannotBeDeletedAndLongListsStayLazy() {
        val tables = mutableStateOf(listOf(Timetable(1, "唯一课表")))
        var switched: Long? = null
        show { TimetableManageScreen(tables.value, 1, {}, { switched = it }, {}, { _, _ -> }, {}) }
        compose.onNodeWithContentDescription("课表「唯一课表」的更多操作").performClick()
        compose.onNodeWithText("删除课表").assertDoesNotExist()
        compose.onNodeWithText("重命名").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { tables.value = (1L..100L).map { Timetable(it, "课表$it") } }
        compose.onNodeWithTag("timetable-card-100").assertDoesNotExist()
        compose.onNodeWithTag("timetables-list").performScrollToNode(hasTestTag("timetable-select-100"))
        compose.onNodeWithTag("timetable-select-100").performClick()
        compose.runOnIdle { assertEquals(100L, switched) }
        // The fixed toolbar keeps creation reachable even at the end of a long list.
        compose.onNodeWithContentDescription("新建课表").assertIsDisplayed().performClick()
        compose.onNodeWithTag("timetable-name-input").assertIsDisplayed()
    }
}
