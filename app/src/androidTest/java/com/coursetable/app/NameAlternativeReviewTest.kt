package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.UnifiedImportReview
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NameAlternativeReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersRefreshAfterCorrectionAndConfirmation() {
        val base = CandidateCourse("普通课程", dayOfWeek = 1, startSection = 1, duration = 2,
            startWeek = 1, endWeek = 16, weekType = 0, draftId = "normal")
        val records = showRecords(listOf(base,
            base.copy(name = "周次待修正", draftId = "invalid", startWeek = 0),
            base.copy(name = "名称待确认", draftId = "suggested", needsReview = true)))
        compose.onNodeWithText("需修正 1").performClick()
        compose.onNodeWithTag("review-course-invalid").assertIsDisplayed()
        compose.onNodeWithTag("review-course-normal").assertDoesNotExist()
        compose.runOnIdle { records.value = records.value.map { if (it.draftId == "invalid") it.copy(startWeek = 1) else it } }
        compose.onNodeWithText("需修正 1").assertDoesNotExist()
        compose.onNodeWithText("全部 3").assertIsDisplayed()
        compose.onNodeWithText("建议确认 1").performClick()
        compose.onNodeWithTag("review-course-suggested").assertIsDisplayed()
        compose.onNodeWithTag("review-course-normal").assertDoesNotExist()
        compose.runOnIdle { records.value = records.value.map { if (it.draftId == "suggested") it.copy(needsReview = false) else it } }
        compose.onNodeWithText("建议确认 1").assertDoesNotExist()
        compose.onNodeWithTag("import-review-content").performScrollToNode(hasTestTag("review-course-normal"))
        compose.onNodeWithTag("review-course-normal").assertIsDisplayed()
    }

    private val suggestion = OcrAlternative("形勢与政策U", "形势与政策U", "名称存在分歧", field = OcrField.NAME)
    private fun batchRecords(): List<CandidateCourse> {
        val first = CandidateCourse("形勢与政策U", "A301", "已校对教师", 1, 1, 2, 4, 16, 0,
            selectedWeeksCsv = "4,9,16", draftId = "first", sourceRegion = "shared-row", needsReview = true,
            alternatives = listOf(suggestion), issues = listOf(suggestion.reason), editedFields = setOf("weeks", "teacher"), reviewFields = setOf(ReviewField.NAME))
        return listOf(first,
            first.copy(draftId = "second", dayOfWeek = 2, startSection = 3, location = "B202", selectedWeeksCsv = "4,10,16"),
            first.copy(draftId = "edited", dayOfWeek = 3, editedFields = first.editedFields + "name"),
            first.copy(draftId = "unrelated", dayOfWeek = 4, sourceRegion = "other-row"),
            first.copy(draftId = "reviewed", dayOfWeek = 5, needsReview = false, issues = emptyList()))
    }

    private fun showRecords(initial: List<CandidateCourse>): MutableState<List<CandidateCourse>> {
        val records = mutableStateOf(initial)
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    UnifiedImportReview(null, AppSettings(), records.value, "图片识别", emptyList(), { records.value = it }, {}, {}, false, true, null)
                }
            }
        }
        return records
    }

    private fun openFirstCandidate() = compose.onNode(hasText("使用候选：形势与政策U") and
        hasAnyAncestor(hasTestTag("review-course-first"))).performScrollTo().performClick()

    @Test fun explicitBatchConfirmationChangesOnlyEligibleSourceRecords() {
        val initial = batchRecords()
        val records = showRecords(initial)
        openFirstCandidate()
        compose.onNodeWithText("同时应用到 2 条记录").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(initial, records.value) }
        openFirstCandidate()
        compose.onNodeWithText("同时应用到 2 条记录").performClick()
        compose.runOnIdle {
            assertEquals(initial.map { if (it.draftId in setOf("first", "second")) applyNameAlternative(it, suggestion) else it }, records.value)
        }
    }

    @Test fun defaultConfirmationStillChangesOnlyTheSelectedRecord() {
        val initial = batchRecords()
        val records = showRecords(initial)
        openFirstCandidate()
        compose.onNodeWithText("同时应用到 2 条记录").assertExists()
        compose.onNodeWithText("采用候选课程名").performClick()
        compose.runOnIdle {
            assertEquals(listOf(applyNameAlternative(initial.first(), suggestion)) + initial.drop(1), records.value)
        }
    }

    @Test fun changedRecordDisablesStaleConfirmation() {
        val initial = batchRecords()
        val records = showRecords(initial)
        openFirstCandidate()
        compose.runOnIdle { records.value = initial.map { if (it.draftId == "first") it.copy(teacher = "更新教师") else it } }
        compose.onNodeWithText("记录已变化，请取消后重新选择候选。").assertExists()
        compose.onNodeWithText("采用候选课程名").assertIsNotEnabled()
        compose.onNodeWithText("同时应用到 2 条记录").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals("更新教师", records.value.first().teacher) }
    }

    @Test fun nameCanBeConfirmedWithoutImageAndOtherReviewedFieldsStayUnchanged() {
        val alternative = OcrAlternative("形勢与政策U", "形势与政策U", "名称存在分歧", field = OcrField.NAME)
        val original = CandidateCourse("形勢与政策U", "A301", "已校对教师", 4, 9, 2, 4, 16, 0,
            selectedWeeksCsv = "4,9,16", draftId = "name-record", needsReview = true,
            alternatives = listOf(alternative), issues = listOf(alternative.reason), editedFields = setOf("weeks", "teacher"), reviewFields = setOf(ReviewField.NAME))
        var records by mutableStateOf(listOf(original))
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    UnifiedImportReview(null, AppSettings(), records, "图片识别", emptyList(), { records = it }, {}, {}, false, true, null)
                }
            }
        }
        compose.onNodeWithText("使用候选：形势与政策U").performScrollTo().performClick()
        compose.onNodeWithText("确认课程名").assertExists()
        compose.onNodeWithText("课程名称 *").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original, records.single()) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(original, records.single()) }
        compose.onNodeWithText("使用候选：形势与政策U").performClick()
        compose.onNodeWithText("采用候选课程名").performClick()
        compose.runOnIdle {
            val actual = records.single()
            assertEquals(alternative.candidate, actual.name)
            assertEquals(original.teacher, actual.teacher)
            assertEquals(original.location, actual.location)
            assertEquals(original.selectedWeeksCsv, actual.selectedWeeksCsv)
            assertEquals(original.dayOfWeek, actual.dayOfWeek)
            assertEquals(original.startSection, actual.startSection)
            assertEquals(original.duration, actual.duration)
            assertEquals(original.editedFields + "name", actual.editedFields)
            assertTrue(actual.alternatives.isEmpty())
            assertFalse(actual.needsReview)
            assertTrue(actual.reviewIssues(AppSettings()).isEmpty())
        }
        compose.onNodeWithText("开始校对").assertDoesNotExist()
    }

    @Test fun confirmingNameLeavesOnlyTheUnresolvedTimePrompt() {
        val time = OcrAlternative("17周", "1-17周", "时间分歧", field = OcrField.TIME)
        val original = batchRecords().first().copy(alternatives = listOf(suggestion, time), issues = listOf(suggestion.reason, time.reason),
            reviewFields = setOf(ReviewField.NAME, ReviewField.TIME))
        val records = showRecords(listOf(original))
        openFirstCandidate()
        compose.onNodeWithText("采用候选课程名").performClick()
        compose.runOnIdle {
            assertEquals(setOf(ReviewField.TIME), records.value.single().reviewFields)
            assertTrue(records.value.single().needsReview)
        }
        compose.onNodeWithText(ReviewField.NAME.prompt).assertDoesNotExist()
        compose.onNodeWithText(ReviewField.TIME.prompt).assertExists()
        compose.onNodeWithText("开始校对").assertExists()
    }

    @Test fun savingTheWholeReviewedRecordClearsPendingFieldsAndOldAlternatives() {
        val time = OcrAlternative("17周", "1-17周", "时间分歧", field = OcrField.TIME)
        val original = batchRecords().first().copy(alternatives = listOf(suggestion, time), issues = listOf(suggestion.reason, time.reason),
            reviewFields = setOf(ReviewField.NAME, ReviewField.TIME))
        val records = showRecords(listOf(original))
        compose.onNodeWithText("开始校对").performClick()
        compose.onNodeWithText("保存并校对下一条").performClick()
        compose.runOnIdle {
            val result = records.value.single()
            assertFalse(result.needsReview)
            assertEquals(emptySet<ReviewField>(), result.reviewFields)
            assertTrue(result.alternatives.isEmpty())
            assertTrue(result.reviewIssues(AppSettings()).isEmpty())
        }
        compose.onNodeWithText("开始校对").assertDoesNotExist()
    }
}
