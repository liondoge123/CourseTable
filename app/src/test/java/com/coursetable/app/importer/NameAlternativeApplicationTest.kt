package com.coursetable.app.importer

import org.junit.Assert.*
import org.junit.Test

class NameAlternativeApplicationTest {
    private val suggestion = OcrAlternative("原课程", "候选课程", "名称分歧", field = OcrField.NAME)
    private fun record(id: String, source: String? = "same-row") = CandidateCourse("原课程", "A301", "教师", 1, 1, 2, 1, 16, 0,
        draftId = id, sourceRegion = source, alternatives = listOf(suggestion), needsReview = true)

    @Test fun relatedRecordsRequireSharedSourceSameCandidateAndUnreviewedName() {
        val first = record("first")
        val same = record("same").copy(dayOfWeek = 4, location = "B202", selectedWeeksCsv = "4,9,16")
        val unrelated = record("unrelated", "other-row")
        val edited = record("edited").copy(editedFields = setOf("name"))
        val teacher = record("teacher").copy(teacher = "另一教师")
        val target = record("target").copy(targetCourseId = 17)
        val separate = record("separate").copy(separateCourse = true)
        val otherSuggestion = record("different").copy(alternatives = listOf(suggestion.copy(candidate = "另一个候选")))
        val noSuggestion = record("none").copy(alternatives = emptyList())
        val reviewed = record("reviewed").copy(needsReview = false, issues = emptyList())
        val list = listOf(first, same, unrelated, edited, teacher, target, separate, otherSuggestion, noSuggestion, reviewed)
        val related = relatedNameAlternativeRecords(list, first, suggestion)
        assertEquals(listOf(first, same), related)
        assertEquals(list.map { if (it.draftId in setOf("first", "same")) applyNameAlternative(it, suggestion) else it },
            applyNameAlternativeRecords(list, related, suggestion))
        val separateFirst = first.copy(separateCourse = true)
        assertEquals(listOf(separateFirst), relatedNameAlternativeRecords(listOf(separateFirst, same), separateFirst, suggestion))
    }

    @Test fun explicitGroupCanRelateDifferentRegionsButMatchingNamesAloneCannot() {
        val first = record("first", "region-a").copy(groupKey = "explicit-course")
        val same = record("same", "region-b").copy(groupKey = "explicit-course")
        val other = record("other", "region-c").copy(groupKey = "other-course")
        assertEquals(listOf(first, same), relatedNameAlternativeRecords(listOf(first, same, other), first, suggestion))
        val noGroup = listOf(first, same).map { it.copy(groupKey = null, sourceRegion = null) }
        assertEquals(listOf(noGroup.first()), relatedNameAlternativeRecords(noGroup, noGroup.first(), suggestion))
        val blankGroup = noGroup.map { it.copy(groupKey = " ", sourceRegion = "") }
        assertEquals(listOf(blankGroup.first()), relatedNameAlternativeRecords(blankGroup, blankGroup.first(), suggestion))
    }

    @Test fun staleSnapshotOrDuplicateIdCannotOverwriteAChangedRecord() {
        val first = record("first")
        val changed = first.copy(teacher = "新教师")
        assertTrue(relatedNameAlternativeRecords(listOf(changed), first, suggestion).isEmpty())
        assertThrows(ImportChangedException::class.java) { applyNameAlternativeRecords(listOf(changed), listOf(first), suggestion) }
        assertThrows(ImportChangedException::class.java) { applyNameAlternativeRecords(listOf(first, first), listOf(first), suggestion) }
        assertThrows(IllegalArgumentException::class.java) { applyNameAlternativeRecords(listOf(first), listOf(first, first), suggestion) }
    }

    @Test fun confirmingNameKeepsAHintThatIsAlsoUsedByAnotherField() {
        val name = suggestion.copy(reason = "共享提示")
        val time = OcrAlternative("17周", "1-17周", "共享提示")
        val original = record("first").copy(alternatives = listOf(name, time), issues = listOf("共享提示"))
        val result = applyNameAlternative(original, name)
        assertEquals(listOf(time), result.alternatives)
        assertEquals(original.issues, result.issues)
    }
    @Test fun explicitNameConfirmationKeepsAllOtherValuesAndReviewedFlags() {
        val name = OcrAlternative("形勢与政策U", "形势与政策U", "名称分歧", field = OcrField.NAME)
        val time = OcrAlternative("17周", "1-17周", "时间分歧")
        val original = CandidateCourse("形勢与政策U", "已校对教室", "已校对教师", 4, 9, 2, 4, 16, 0,
            selectedWeeksCsv = "4,9,16", draftId = "record", needsReview = true,
            alternatives = listOf(name, time), issues = listOf(name.reason, time.reason), editedFields = setOf("weeks", "teacher"))
        val updated = applyNameAlternative(original, name)
        assertEquals(original.copy(name = name.candidate, editedFields = original.editedFields + "name",
            alternatives = listOf(time), issues = listOf(time.reason)), updated)
        val retry = updated.copy(name = "重新识别名称", teacher = "错误教师", selectedWeeksCsv = "1,2,3")
        val preserved = preserveReviewedFields(updated, retry)
        assertEquals(updated.name, preserved.name)
        assertEquals(updated.teacher, preserved.teacher)
        assertEquals(updated.selectedWeeksCsv, preserved.selectedWeeksCsv)
    }

    @Test fun invalidOrUnscopedAlternativeCannotBeAppliedAsAName() {
        val candidate = CandidateCourse("课程", "", "", 1, 1, 2, 1, 16, 0)
        for (alternative in listOf(OcrAlternative("课程", "其他", "未指定字段"),
            OcrAlternative("课程", "", "空名称", field = OcrField.NAME),
            OcrAlternative("课程", "x".repeat(ImportPolicy.MAX_TEXT_FIELD + 1), "过长", field = OcrField.NAME))) {
            assertFalse(usableNameAlternative(alternative))
            assertThrows(IllegalArgumentException::class.java) { applyNameAlternative(candidate, alternative) }
        }
    }
}
