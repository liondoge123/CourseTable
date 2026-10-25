package com.coursetable.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class ScrollPositionIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
    private val created = mutableListOf<Long>()
    private var originalId: Long? = null
    private var originalPaste: String? = null

    private fun seed(): Pair<Long, Long> = runBlocking {
        originalId = app.settingsRepository.settings.first { it.timetableId > 0 }.timetableId
        val tables = listOf("滚动回归 A", "滚动回归 B").map { name ->
            val id = app.timetableRepository.create(name)
            created += id
            app.database.courseDao().importCourses(id, (1..60).map { i ->
                Course(timetableId = id, name = "$name-${i.toString().padStart(2, '0')}",
                    dayOfWeek = 1, startSection = 1, duration = 1, startWeek = 1, endWeek = 18)
            }, false)
            id
        }
        app.settingsRepository.setActiveTimetable(tables.first())
        tables[0] to tables[1]
    }

    @After fun cleanup(): Unit = runBlocking {
        originalId?.let { app.settingsRepository.setActiveTimetable(it) }
        created.forEach { app.timetableRepository.delete(it) }
        originalPaste?.let { com.coursetable.app.ui.PasteDraftRepository.get(app).save(it) }
    }

    private fun tab(label: String) {
        compose.onAllNodesWithText(label, substring = false)[0].performClick()
        compose.waitForIdle()
    }

    private fun position(tag: String): Float = compose.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun courseTabAndBackgroundKeepPositionButSwitchingTablesResetsIt() {
        val (_, second) = seed()
        tab("课程")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("课程（60 门）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("courses-list").performScrollToIndex(35)
        val before = position("courses-list")
        assertTrue(before > 1f)
        tab("导入")
        tab("课程")
        assertEquals(before, position("courses-list"), .01f)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(before, position("courses-list"), .01f)
        runBlocking { app.settingsRepository.setActiveTimetable(second) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("滚动回归 B-01").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0f, position("courses-list"), .01f)
    }

    @Test fun timetableGridResetsForTableChangesAndCommittedReplacement() {
        val (first, second) = seed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("滚动回归 A", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("timetable-scroll").performTouchInput { swipeUp() }
        assertTrue(position("timetable-scroll") > 0f)
        runBlocking { app.settingsRepository.setActiveTimetable(second) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("滚动回归 B", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0f, position("timetable-scroll"), .01f)
        compose.onNodeWithTag("timetable-scroll").performTouchInput { swipeUp() }
        assertTrue(position("timetable-scroll") > 0f)
        compose.runOnIdle { app.timetableScrollResets.onOverwriteCommitted(second) }
        assertEquals(0f, position("timetable-scroll"), .01f)
        runBlocking { app.settingsRepository.setActiveTimetable(first) }
    }

    @Test fun actualOverwriteImportInvalidatesAnOffscreenCourseList() {
        val (first, _) = seed()
        originalPaste = runBlocking { com.coursetable.app.ui.PasteDraftRepository.get(app).load() }
        tab("课程")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("课程（60 门）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("courses-list").performScrollToIndex(35)
        assertTrue(position("courses-list") > 1f)
        tab("导入")
        compose.onNodeWithText("粘贴导入", substring = false).performScrollTo().performClick()
        val json = JSONObject().put("courses", JSONArray().apply {
            repeat(60) { i -> put(JSONObject().put("name", "覆盖新课${i.toString().padStart(2, '0')}")
                .put("dayOfWeek", 5).put("startSection", 2).put("duration", 1)
                .put("weeks", JSONArray(listOf(1, 2)))) }
        }).toString()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("paste-import-content").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithTag("paste-import-content").performSemanticsAction(SemanticsActions.SetText) {
            it(AnnotatedString(json))
        }
        compose.onNodeWithTag("paste-import-parse").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("导入确认", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("追加 ▾", substring = true).performClick()
        compose.onNodeWithText("新建课表 ▾", substring = true).performClick()
        compose.onNodeWithText("覆盖 ▾", substring = true).assertExists()
        val revision = app.timetableScrollResets.revisions.value[first]
        compose.onNodeWithText("导入 60 条记录", substring = false).performClick()
        compose.onNodeWithText("覆盖并导入", substring = false).performClick()
        compose.waitUntil(10_000) { app.timetableScrollResets.revisions.value[first] != revision }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-review-page").fetchSemanticsNodes().isEmpty() }
        tab("课程")
        assertEquals(0f, position("courses-list"), .01f)
        assertEquals(60, runBlocking { app.courseRepository.byTimetableOnce(first).size })
        compose.onNodeWithText("覆盖新课00").assertIsDisplayed()
    }

    @Test fun secondaryPageReopensAtTopAndKeepsItsParentPosition() {
        tab("设置")
        compose.onNodeWithText("关于 CourseTable", substring = false).performScrollTo()
        val parentBefore = position("settings-scroll")
        assertTrue(parentBefore > 0f)
        compose.onNodeWithText("关于 CourseTable", substring = false).performClick()
        compose.onNodeWithTag("about-scroll").performTouchInput { swipeUp() }
        assertTrue(position("about-scroll") > 0f)
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(parentBefore, position("settings-scroll"), 1f)
        compose.onNodeWithText("关于 CourseTable", substring = false).performClick()
        assertEquals(0f, position("about-scroll"), 1f)
    }
}
