package com.coursetable.app.importer
import com.coursetable.app.data.scheduledWeeks

internal fun usableNameAlternative(alternative: OcrAlternative): Boolean = alternative.field == OcrField.NAME &&
    alternative.candidate.isNotBlank() && alternative.candidate.length <= ImportPolicy.MAX_TEXT_FIELD

/** Explicitly confirmed name changes must not reparse or modify another reviewed field. */
internal fun applyNameAlternative(original: CandidateCourse, alternative: OcrAlternative): CandidateCourse {
    require(usableNameAlternative(alternative))
    val remaining = original.alternatives.filterNot { it.field == OcrField.NAME }
    val nameReasons = original.alternatives.filter { it.field == OcrField.NAME }.map { it.reason }.toSet() - remaining.map { it.reason }.toSet()
    val issues = original.issues.filterNot { it in nameReasons }
    val fields = original.reviewFields?.minus(ReviewField.NAME)?.plus(remaining.pendingReviewFields())
    return original.copy(name = alternative.candidate, editedFields = original.editedFields + "name",
        alternatives = remaining, issues = issues, reviewFields = fields,
        needsReview = if (fields == null) original.needsReview else fields.isNotEmpty() || remaining.isNotEmpty() || issues.isNotEmpty())
}

/** Group only explicit shared provenance, never matching names alone. */
internal fun relatedNameAlternativeRecords(records: List<CandidateCourse>, original: CandidateCourse,
    alternative: OcrAlternative): List<CandidateCourse> {
    val id = original.draftId ?: return emptyList()
    if (!usableNameAlternative(alternative) || records.singleOrNull { it.draftId == id } != original) return emptyList()
    val sourceIds = original.sourceRegionIds().filter { it.isNotBlank() }.toSet()
    val related = records.filter { other ->
        other.draftId != null && other.draftId != id && !original.separateCourse && !other.separateCourse &&
            other.needsReview && "name" !in other.editedFields && other.targetCourseId == original.targetCourseId &&
            normalizedName(other.name) == normalizedName(original.name) && normalizedName(other.teacher) == normalizedName(original.teacher) &&
            ((!original.groupKey.isNullOrBlank() && other.groupKey == original.groupKey) || other.sourceRegionIds().any { it in sourceIds }) &&
            other.alternatives.any { usableNameAlternative(it) && normalizedName(it.candidate) == normalizedName(alternative.candidate) }
    }
    return listOf(original) + related
}

/** Apply precisely the records previewed by the user, preserving their order and other fields. */
internal fun applyNameAlternativeRecords(records: List<CandidateCourse>, reviewed: List<CandidateCourse>,
    alternative: OcrAlternative): List<CandidateCourse> {
    require(reviewed.isNotEmpty() && usableNameAlternative(alternative))
    val ids = reviewed.map { requireNotNull(it.draftId) }.toSet()
    require(ids.size == reviewed.size)
    val currentById = records.groupBy { it.draftId }
    if (reviewed.any { before -> currentById[before.draftId]?.singleOrNull() != before }) throw ImportChangedException()
    return records.map { if (it.draftId in ids) applyNameAlternative(it, alternative) else it }
}

/** A retry proposes changes; explicitly edited fields are never silently replaced. */
fun preserveReviewedFields(original: CandidateCourse, recognized: CandidateCourse): CandidateCourse {
    val protected = buildSet {
        if ("name" in original.editedFields) add(ReviewField.NAME)
        if ("teacher" in original.editedFields) add(ReviewField.TEACHER)
        if ("location" in original.editedFields) add(ReviewField.LOCATION)
        if ("dayOfWeek" in original.editedFields) add(ReviewField.DAY_OF_WEEK)
        if ("startSection" in original.editedFields && "duration" in original.editedFields) add(ReviewField.SECTIONS)
        if ("weeks" in original.editedFields) add(ReviewField.WEEKS)
        if (original.editedFields.containsAll(listOf("location", "dayOfWeek", "startSection", "duration", "weeks"))) add(ReviewField.TIME)
    }
    val alternatives = recognized.alternatives.filterNot {
        it.field == OcrField.NAME && ReviewField.NAME in protected || it.field == OcrField.TIME && ReviewField.TIME in protected
    }
    val removedReasons = (recognized.alternatives - alternatives.toSet()).map { it.reason }.toSet() - alternatives.map { it.reason }.toSet()
    val resolvedReasons = buildSet {
        if (ReviewField.WEEKS in protected && recognized.reviewFields?.contains(ReviewField.WEEKS) == true) add("周次缺失，请选择实际上课周次")
        if (ReviewField.DAY_OF_WEEK in protected && recognized.reviewFields?.contains(ReviewField.DAY_OF_WEEK) == true) add("星期文字与表格位置不一致")
    }
    val issues = recognized.issues.filterNot { it in removedReasons || it in resolvedReasons }
    val fields = recognized.reviewFields?.minus(protected)?.plus(alternatives.pendingReviewFields())
    return recognized.copy(
    draftId = original.draftId, included = original.included, targetCourseId = original.targetCourseId, separateCourse = original.separateCourse,
    updateDuplicate = original.updateDuplicate, color = original.color, groupKey = original.groupKey, editedFields = original.editedFields,
    sourceRegions = original.sourceRegions + recognized.sourceRegions, sourceRegion = original.sourceRegion,
    name = if("name" in original.editedFields) original.name else recognized.name,
    teacher = if("teacher" in original.editedFields) original.teacher else recognized.teacher,
    location = if("location" in original.editedFields) original.location else recognized.location,
    dayOfWeek = if("dayOfWeek" in original.editedFields) original.dayOfWeek else recognized.dayOfWeek,
    startSection = if("startSection" in original.editedFields) original.startSection else recognized.startSection,
    duration = if("duration" in original.editedFields) original.duration else recognized.duration,
    startWeek = if("weeks" in original.editedFields) original.startWeek else recognized.startWeek,
    endWeek = if("weeks" in original.editedFields) original.endWeek else recognized.endWeek,
    weekType = if("weeks" in original.editedFields) original.weekType else recognized.weekType,
    selectedWeeksCsv = if("weeks" in original.editedFields) original.selectedWeeksCsv else recognized.selectedWeeksCsv,
    alternatives = alternatives, issues = issues, reviewFields = fields,
    needsReview = if (fields == null) recognized.needsReview else fields.isNotEmpty() || alternatives.isNotEmpty() || issues.isNotEmpty()
    )
}

fun reviewDifferences(a: CandidateCourse, b: CandidateCourse): List<String> = buildList {
    if(a.name != b.name) add("课程名：${a.name} → ${b.name}")
    if(a.teacher != b.teacher) add("教师：${a.teacher} → ${b.teacher}")
    if(a.location != b.location) add("教室：${a.location} → ${b.location}")
    if(a.dayOfWeek != b.dayOfWeek) add("星期：${a.dayOfWeek} → ${b.dayOfWeek}")
    if(a.startSection != b.startSection || a.duration != b.duration) add("节次：${a.startSection}起 ${a.duration}节 → ${b.startSection}起 ${b.duration}节")
    if(a.asCourse().scheduledWeeks() != b.asCourse().scheduledWeeks()) add("周次：${a.asCourse().scheduledWeeks().joinToString()} → ${b.asCourse().scheduledWeeks().joinToString()}")
}
