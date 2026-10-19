package com.coursetable.app.importer

import androidx.room.withTransaction
import com.coursetable.app.data.*

/** Recheck and commit against one transaction snapshot. */
internal suspend fun AppDatabase.commitImportReference(request: ImportRequest, settings: AppSettings): ImportSummary = withTransaction {
    val dao = courseDao()
    val current = dao.byTimetableOnce(settings.timetableId)
    if (request.mode != ImportMode.NEW && current.sortedBy { it.id } != request.reviewedExisting.sortedBy { it.id }) throw ImportChangedException()
    val target = if (request.mode == ImportMode.NEW) timetableDao().upsert((request.timetable ?: timetableDao().byId(settings.timetableId) ?: Timetable(name = "导入的课表")).copy(id = 0)) else settings.timetableId
    val targetSettings = if (request.mode == ImportMode.NEW && request.timetable != null) settings.copy(timetableId = target,
        totalWeeks = request.timetable.totalWeeks, semesterStart = request.timetable.semesterStart(), periods = request.timetable.periods()) else settings.copy(timetableId = target)
    val rows = ScheduleAnalysis.analyze(request.candidates, current, targetSettings, request.mode)
    require(rows.none { it.included && (it.status == ImportStatus.INVALID || it.requiresTarget) }) { "请修正字段或选择同名课程的归属" }
    if (request.mode == ImportMode.OVERWRITE) dao.deleteByTimetable(target)
    var courses = 0; var sessions = 0; var updated = 0; var skipped = 0
    val batchGroups = mutableMapOf<String, Long>()
    for (row in rows) {
        val c = row.candidate
        if (!row.included) { skipped++; continue }
        val live = dao.byTimetableOnce(target)
        val incoming = c.asCourse(target)
        val duplicate = if (c.separateCourse) null else live.firstOrNull { scheduleFingerprint(it) == scheduleFingerprint(incoming) && (c.targetCourseId == null || it.courseId == c.targetCourseId) && (c.groupKey == null || it.courseId == batchGroups[c.groupKey]) }
        if (duplicate != null) {
            if (c.updateDuplicate) {
                dao.upsert(duplicate.copy(teacher = c.teacher.ifBlank { duplicate.teacher }, location = c.location.ifBlank { duplicate.location }))
                updated++
            } else skipped++
            continue
        }
        val candidates = live.filter { normalizedName(it.name) == normalizedName(c.name) }.distinctBy { it.courseId }
        val compatible = candidates.filter { teachersCompatible(it.teacher, c.teacher) }
        val explicit = c.targetCourseId?.let { id -> live.firstOrNull { it.courseId == id } ?: error("目标课程已删除") }
        val key = c.groupKey
        val remembered = key?.let { batchGroups[it] }?.let { id -> live.firstOrNull { it.courseId == id } }
        val parent = explicit ?: remembered ?: if (!c.separateCourse && key == null) compatible.singleOrNull() else null
        val saved = if (parent == null) incoming else incoming.copy(courseId = parent.courseId, name = parent.name,
            teacher = parent.teacher.ifBlank { c.teacher }, color = parent.color)
        val id = dao.upsert(saved)
        if (parent == null) courses++
        sessions++
        if (key != null) batchGroups[key] = dao.byId(id)!!.courseId
    }
    ImportSummary(target, courses, sessions, updated, skipped)
}
