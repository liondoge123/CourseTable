package com.coursetable.app.importer

import com.coursetable.app.data.*

/** Previous traversal retained only for differential tests and measurement. */
internal object ReferenceScheduleAnalysis {
    fun analyze(candidates: List<CandidateCourse>, existing: List<Course>, settings: AppSettings, mode: ImportMode): List<AnalyzedRow> {
        val baseline = if (mode == ImportMode.APPEND) existing else emptyList()
        val accepted = mutableListOf<Pair<Course, String?>>()
        return candidates.map { candidate ->
            val course = candidate.asCourse(settings.timetableId)
            val duplicatePool = baseline + accepted.filter { candidate.groupKey == null || it.second == candidate.groupKey }.map { it.first }
            val duplicate = if (candidate.separateCourse) null else duplicatePool.firstOrNull { scheduleFingerprint(it) == scheduleFingerprint(course) && (candidate.targetCourseId == null || it.courseId == candidate.targetCourseId) }
            val targets = baseline.filter { normalizedName(it.name) == normalizedName(course.name) }.distinctBy { it.courseId }
            val errors = candidate.fieldErrors(settings)
            val conflicts = if (errors.isEmpty()) scheduleConflicts(course, baseline + accepted.map { it.first }).filterNot { it.course == duplicate } else emptyList()
            val status = when { errors.isNotEmpty() -> ImportStatus.INVALID; duplicate != null -> ImportStatus.DUPLICATE; conflicts.isNotEmpty() -> ImportStatus.CONFLICT; else -> ImportStatus.NORMAL }
            val row = AnalyzedRow(candidate, status, duplicate, targets, conflicts)
            if (row.included && status != ImportStatus.INVALID && (duplicate == null || candidate.updateDuplicate || candidate.separateCourse)) accepted += course to candidate.groupKey
            row
        }
    }
}
